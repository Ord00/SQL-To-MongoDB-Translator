package sql.to.mongodb.translator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sql.to.mongodb.translator.config.RabbitMQConfig;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.listener.TestIRGeneratorListener;
import sql.to.mongodb.translator.requests.ScannerRequest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(RabbitMQConfig.class)
public class CodeGeneratorTest {
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

    static GenericContainer<?> scanner;
    static GenericContainer<?> parser;
    static GenericContainer<?> irGenerator;

    @BeforeAll
    static void startContainers() {
        String rabbitmqHost = "host.docker.internal";
        int rabbitmqPort = rabbitmq.getMappedPort(5672);

        scanner = new GenericContainer<>("sql-to-mongodb-translator-scanner:latest")
                .withNetwork(NETWORK)
                .withEnv("RABBIT_USER", "guest")
                .withEnv("RABBIT_PASSWORD", "guest")
                .withEnv("RABBIT_SERVICE", rabbitmqHost)
                .withEnv("RABBIT_PORT", String.valueOf(rabbitmqPort))
                .waitingFor(Wait.forLogMessage(".*Started ScannerApplication.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));

        scanner.start();

        parser = new GenericContainer<>("sql-to-mongodb-translator-parser:latest")
                .withNetwork(NETWORK)
                .withEnv("RABBIT_USER", "guest")
                .withEnv("RABBIT_PASSWORD", "guest")
                .withEnv("RABBIT_SERVICE", rabbitmqHost)
                .withEnv("RABBIT_PORT", String.valueOf(rabbitmqPort))
                .waitingFor(Wait.forLogMessage(".*Started ParserApplication.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));

        parser.start();

        parser = new GenericContainer<>("sql-to-mongodb-translator-ir-generator:latest")
                .withNetwork(NETWORK)
                .withEnv("RABBIT_USER", "guest")
                .withEnv("RABBIT_PASSWORD", "guest")
                .withEnv("RABBIT_SERVICE", rabbitmqHost)
                .withEnv("RABBIT_PORT", String.valueOf(rabbitmqPort))
                .waitingFor(Wait.forLogMessage(".*Started IRGeneratorApplication.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));

        parser.start();
    }

    @AfterAll
    static void stopContainers() {
        if (scanner != null) {
            scanner.stop();
        }
        if (parser != null) {
            parser.stop();
        }
        if (irGenerator != null) {
            irGenerator.stop();
        }
        if (rabbitmq != null) {
            rabbitmq.stop();
        }
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CodeGenerator codeGenerator;

    private static final String SCANNER_QUEUE = "scanner_queue";
    private static final String CODE_GENERATOR_QUEUE = "code_generator_queue";

    private String getCodeGeneratorResult(String sql) throws CodeGenerationException {
        ScannerRequest request = new ScannerRequest(sql);
        String correlationId = UUID.randomUUID().toString();

        CountDownLatch latch = new CountDownLatch(1);
        TestIRGeneratorListener.latches.put(correlationId, latch);

        rabbitTemplate.convertAndSend(SCANNER_QUEUE, request, message -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setReplyTo(CODE_GENERATOR_QUEUE);
            message.getMessageProperties().setContentType("application/json");
            return message;
        });

        try {
            if (latch.await(10, TimeUnit.SECONDS)) {
                return codeGenerator.generate(TestIRGeneratorListener.responses.remove(correlationId));
            }
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    @Test
    public void columnNamesTest() throws CodeGenerationException {

        Assertions.assertEquals(
                "db.collection.find({})",
                getCodeGeneratorResult("SELECT * FROM collection")
        );
        Assertions.assertEquals(
                "db.collection.find({}, {column_name: 1})",
                getCodeGeneratorResult("SELECT column_name FROM collection")
        );
        Assertions.assertEquals(
                "db.collection.find({}, {a: 1, b: 1, c: 1, d: 1})",
                getCodeGeneratorResult("SELECT a, b, c, d FROM collection")
        );
    }

    @Test
    public void wherePartTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({age: {$gt: 22}})",
                getCodeGeneratorResult("SELECT * FROM collection WHERE age > 22")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$lt: 'abcd'}})",
                getCodeGeneratorResult("SELECT * FROM collection WHERE name < 'abcd'")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$eq: -11}})",
                getCodeGeneratorResult("SELECT * FROM collection WHERE income = -11")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$ne: 'abcd'}})",
                getCodeGeneratorResult("SELECT * FROM collection WHERE 'abcd' <> name")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$gt: -11}})",
                getCodeGeneratorResult("SELECT * FROM collection WHERE -11 < income")
        );
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection WHERE income < age"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection WHERE -11 <> 22"));
    }

    @Test
    public void skipLimitPartTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({}).limit(10)",
                getCodeGeneratorResult("SELECT * FROM collection LIMIT 10")
        );
        Assertions.assertEquals(
                "db.collection.find({}).skip(2)",
                getCodeGeneratorResult("SELECT * FROM collection OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({}).limit(10).skip(2)",
                getCodeGeneratorResult("SELECT * FROM collection LIMIT 10 OFFSET 2")
        );

        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection LIMIT 10 LIMIT 2"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection OFFSET 2 OFFSET 10"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection LIMIT '10'"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection OFFSET -2"));
    }

    @Test
    public void combinationTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({age: {$ne: 22}}, {column_name: 1})",
                getCodeGeneratorResult("SELECT column_name FROM collection WHERE age <> 22")
        );
        Assertions.assertEquals(
                "db.collection.find({}, {a: 1, b: 1, c: 1, d: 1}).limit(10).skip(2)",
                getCodeGeneratorResult("SELECT a, b, c, d FROM collection LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$lt: -11}}).limit(10).skip(2)",
                getCodeGeneratorResult("SELECT * FROM collection WHERE income < -11 LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$eq: 'abcd'}}).limit(10).skip(2)",
                getCodeGeneratorResult("SELECT * FROM collection WHERE name = 'abcd' LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({age: {$lt: 22}}, {column_name: 1}).limit(10)",
                getCodeGeneratorResult("SELECT column_name FROM collection WHERE 22 > age LIMIT 10")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$ne: -11}}, {a: 1, b: 1, c: 1, d: 1}).skip(2)",
                getCodeGeneratorResult("SELECT a, b, c, d FROM collection WHERE -11 <> income OFFSET 2")
        );
    }

    @Test
    public void parsingTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({age: {$gt: 22}}, {a: 1, b: 1, c: 1, d: 1}).limit(10).skip(2)",
                getCodeGeneratorResult("SeLeCt a, b, c, d fRoM collection whEre age > 22 liMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({age: {$gt: 22}}, {a: 1, b: 1, c: 1, d: 1}).limit(10).skip(2)",
                getCodeGeneratorResult("   SELECT  a,b,   c  ,d    FROM  collection   " +
                        " WHERE age> 22   LIMIT 10 OFFSET  2    ")
        );
        Assertions.assertEquals(
                "db.c0lL__EcTi0n.find({n23_ame: {$gt: ''}}, {a1_A2_: 1, _b_B: 1, C13: 1, _Dd_1_23_: 1})",
                getCodeGeneratorResult("SELECT a1_A2_, _b_B, C13, _Dd_1_23_ FROM c0lL__EcTi0n WHERE n23_ame > ''")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$gt: 'a\\'b\\\\\\cd\\''}}, {a: 1, b: 1, c: 1, d: 1})",
                getCodeGeneratorResult("SELECt a, b, c, d FROM collection WHERE name > 'a\\'b\\\\\\cd\\''")
        );
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM 0_nameStartFromDigit"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection WHERE22 < age"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection WHERE age > 22LIMIT 10"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection WHERE name = 'ab'cd'"));
        Assertions.assertNull(getCodeGeneratorResult("SELECT * FROM collection WHERE 'abcd\\' = name"));
    }
}
