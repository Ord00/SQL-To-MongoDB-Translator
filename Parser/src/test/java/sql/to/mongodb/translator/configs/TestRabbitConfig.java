package sql.to.mongodb.translator.configs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestRabbitConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public DirectExchange scannerExchange() {
        return new DirectExchange("scanner.exchange");
    }

    @Bean
    public Queue parserQueue() {
        return new Queue("parser_queue", false);
    }

    @Bean
    public Binding requestBinding(Queue scanRequestQueue, DirectExchange scannerExchange) {
        return BindingBuilder
                .bind(scanRequestQueue)
                .to(scannerExchange)
                .with("scanner.request");
    }

    @Bean
    public Binding responseBinding(Queue scanResponseQueue, DirectExchange scannerExchange) {
        return BindingBuilder
                .bind(scanResponseQueue)
                .to(scannerExchange)
                .with("scanner.response");
    }
}
