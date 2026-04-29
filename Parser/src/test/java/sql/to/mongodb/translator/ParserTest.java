package sql.to.mongodb.translator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sql.to.mongodb.translator.config.RabbitMQConfig;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.requests.ParserRequest;
import sql.to.mongodb.translator.requests.ScannerRequest;
import sql.to.mongodb.translator.scanner.Token;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(RabbitMQConfig.class)
public class ParserTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static RabbitMQContainer rabbitmq =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine")
                    .withNetwork(NETWORK)
                    .withNetworkAliases("rabbitmq-test")
                    .withExposedPorts(5672);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getFirstMappedPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @BeforeEach
    void setupRabbitTemplate() {
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());

        System.out.println("=== RabbitTemplate конфигурация ===");
        System.out.println("MessageConverter: " + rabbitTemplate.getMessageConverter().getClass().getSimpleName());

        rabbitTemplate.execute(channel -> {
            com.rabbitmq.client.AMQP.Queue.DeclareOk response = channel.queueDeclarePassive(PARSER_QUEUE);
            System.out.println("Messages in queue '" + PARSER_QUEUE + "': " + response.getMessageCount());
            System.out.println("Consumers on queue: " + response.getConsumerCount());
            return null;
        });
    }

    static GenericContainer<?> scanner;

    @BeforeAll
    static void startScanner() {
        String rabbitmqHost = "host.docker.internal";
        int rabbitmqPort = rabbitmq.getMappedPort(5672);

        System.out.println("RabbitMQ host: " + rabbitmqHost);
        System.out.println("RabbitMQ port: " + rabbitmqPort);

        scanner = new GenericContainer<>("sql-to-mongodb-translator-scanner:latest")
                .withNetwork(NETWORK)
                .withEnv("RABBIT_USER", "guest")
                .withEnv("RABBIT_PASSWORD", "guest")
                .withEnv("RABBIT_SERVICE", rabbitmqHost)
                .withEnv("RABBIT_PORT", String.valueOf(rabbitmqPort))
                // Включаем DEBUG логирование для Spring AMQP
                .withEnv("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AMQP", "DEBUG")
                .withEnv("LOGGING_LEVEL_COM_RABBITMQ", "DEBUG")
                .withLogConsumer(outputFrame -> {
                    String line = outputFrame.getUtf8String();
                    System.out.print("[SCANNER] " + line);
                    if (line.contains("received") || line.contains("Received")) {
                        System.out.println(">>> Scanner получил сообщение!");
                    }
                })
                .waitingFor(Wait.forLogMessage(".*Started ScannerApplication.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));

        scanner.start();
        System.out.println("Scanner started successfully!");
    }

    @AfterAll
    static void stopScanner() {
        if (scanner != null) {
            scanner.stop();
        }
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private Parser parser;

    private static final String SCANNER_QUEUE = "scanner_queue";
    private static final String PARSER_QUEUE = "parser_queue";

    private List<Token> sendSqlToScannerAndGetTokens(String sql) {
        ScannerRequest request = new ScannerRequest(sql);
        String correlationId = UUID.randomUUID().toString();

        rabbitTemplate.convertAndSend(SCANNER_QUEUE, request, message -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setReplyTo(PARSER_QUEUE);
            message.getMessageProperties().setContentType("application/json");
            return message;
        });

        Object response = rabbitTemplate.receiveAndConvert(PARSER_QUEUE, 30000);
        return ((ParserRequest) response).lexicalResult();
    }

    @Test
    public void testInOfOneSubquery() {
        String codeToScan = """
                SELECT id, name, file
                FROM products
                WHERE id IN (SELECT product_id
                             FROM sales)""";

        // Получаем реальные токены от Scanner через RabbitMQ
        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);

        // Передаем токены в Parser
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testExistsAndJoin() {
        String codeToScan = """
                SELECT Tm.TeamName
                FROM Team Tm
                WHERE NOT EXISTS (SELECT 1
                                  FROM Competition Comp2
                                  WHERE (SELECT 1
                                        FROM Race R3 JOIN StaffRace SR3
                                        ON R3.Id_race = SR3.Race
                                        JOIN Staff S3
                                        ON SR3.Staff = S3.Id_staff
                                        JOIN TeamStaff TS3
                                        ON S3.Id_staff = TS3.Staff
                                        JOIN Team Tm3
                                        ON TS3.Team = Tm3.Id_team
                                        WHERE RaceDate >= EntryDate
                                                AND (TS3.ExitDate IS NULL OR R3.RaceDate <= TS3.ExitDate)
                                                AND Tm.Id_team = Tm3.Id_team
                                                AND Comp2.Id_competition = R3.Competition
                                        ) >= 1
                                )""";

        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testAllWithSpecificTable() {
        String codeToScan = """
                SELECT CompetitionName, Race.*
                FROM Competition LEFT JOIN Race
                     ON Id_competition = Competition""";

        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testAllFunction() {
        String codeToScan = """
                SELECT TP.Id_team, TP.TeamName
                FROM TeamProfit TP
                WHERE TP.Profit >= ALL(SELECT TP2.Profit
                                       FROM TeamProfit TP2)""";

        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testOrderBy() {
        String codeToScan = """
                SELECT R.*, R.TicketPrice * R.SoldTickets AS Profit
                FROM Race R
                ORDER BY R.TicketPrice * R.SoldTickets DESC""";

        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testLimit() {
        String codeToScan = """
                SELECT DISTINCT Cn.Id_country, Cn.CountryName
                FROM Race R RIGHT JOIN StaffRace SR
                    ON R.Id_race = SR.Race
                    RIGHT JOIN Staff S
                    ON SR.Staff = S.Id_staff
                    RIGHT JOIN TeamStaff TS
                    ON S.Id_staff = TS.Staff
                    RIGHT JOIN Team Tm
                    ON TS.Team = Tm.Id_team
                    RIGHT JOIN Country Cn
                    ON Tm.Country = Cn.Id_country
                WHERE R.RaceDate >= TS.EntryDate
                    AND (TS.ExitDate IS NULL OR R.RaceDate <= TS.ExitDate)
                    AND R.TicketPrice * R.SoldTickets IN (SELECT R.TicketPrice * R.SoldTickets AS Profit
                                                          FROM Race R
                                                          ORDER BY Profit DESC
                                                          LIMIT 3)""";

        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);

        Node result = Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));

        System.out.println(result);
    }

    @Test
    public void testCaseAsAggregateAttribute() {
        String codeToScan = """
                SELECT (CalcRes.ChampionshipNum * 100) / CalcRes.Total AS Championship,
                       (CalcRes.CupNum * 100) / CalcRes.Total AS Cup,
                       (CalcRes.PrecedenceNum * 100) / CalcRes.Total AS Precedence
                FROM (SELECT SUM(CASE WHEN CT.CompetitionTypeName = 'Чемпионат'
                                      THEN 1
                                      ELSE 0
                                      END) ChampionshipNum,
                             SUM(CASE WHEN CT.CompetitionTypeName = 'Кубок'
                                      THEN 1
                                      ELSE 0
                                      END) CupNum,
                             SUM(CASE WHEN CT.CompetitionTypeName = 'Первенство'
                                      THEN 1
                                      ELSE 0
                                      END) PrecedenceNum
                      FROM Competition Comp JOIN CompetitionType CT
                        ON Comp.CompetitionType = CT.Id_competition_type
                     ) AS CalcRes""";

        List<Token> tokens = sendSqlToScannerAndGetTokens(codeToScan);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }
}