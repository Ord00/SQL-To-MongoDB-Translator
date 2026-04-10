package sql.to.mongodb.translator.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.scanner.queue.name}")
    private String scannerQueueName;

    @Value("${rabbitmq.parser.queue.name}")
    private String parserQueueName;

    @Value("${rabbitmq.ir.generator.queue.name}")
    private String irGeneratorQueueName;

    @Value("${rabbitmq.code.generator.queue.name}")
    private String codeGeneratorQueueName;

    @Value("${rabbitmq.response.queue.name}")
    private String responseQueueName;

    @Bean
    public Queue scannerQueue() {
        return new Queue(scannerQueueName, true);
    }

    @Bean
    public Queue parserQueue() {
        return new Queue(parserQueueName, true);
    }

    @Bean
    public Queue irGeneratorQueue() {
        return new Queue(irGeneratorQueueName, true);
    }

    @Bean
    public Queue codeGeneratorQueue() {
        return new Queue(codeGeneratorQueueName, true);
    }

    @Bean
    public Queue responseQueue() {
        return new Queue(responseQueueName, true);
    }
}
