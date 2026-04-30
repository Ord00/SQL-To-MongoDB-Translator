package sql.to.mongodb.translator.helper;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public class TestContainersHelper {
    private static final Network NETWORK = Network.newNetwork();

    @DynamicPropertySource
    public static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getFirstMappedPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    private static RabbitMQContainer rabbitmq;
    private static GenericContainer<?> scanner;
    private static GenericContainer<?> parser;

    public static void startContainers() {
        rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
                .withNetwork(NETWORK)
                .withNetworkAliases("rabbitmq-test")
                .withExposedPorts(5672);

        rabbitmq.start();

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
    }

    public static void stopContainers() {
        if (scanner != null) {
            scanner.stop();
        }
        if (parser != null) {
            parser.stop();
        }
        if (rabbitmq != null) {
            rabbitmq.stop();
        }
    }
}
