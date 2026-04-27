package sql.to.mongodb.translator.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.setVisibility(
                objectMapper.getVisibilityChecker()
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
        );
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
