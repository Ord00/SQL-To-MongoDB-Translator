package sql.to.mongodb.translator.helper;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.listener.TestParserListener;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.requests.ScannerRequest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@Profile("test")
public class RabbitMQTestHelper {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String SCANNER_QUEUE = "scanner_queue";
    private static final String IR_GENERATOR_QUEUE = "ir_generator_queue";

    public Node getParserResult(String sql) {
        ScannerRequest request = new ScannerRequest(sql);
        String correlationId = UUID.randomUUID().toString();

        CountDownLatch latch = new CountDownLatch(1);
        TestParserListener.latches.put(correlationId, latch);

        rabbitTemplate.convertAndSend(SCANNER_QUEUE, request, message -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setReplyTo(IR_GENERATOR_QUEUE);
            message.getMessageProperties().setContentType("application/json");
            return message;
        });

        try {
            if (latch.await(10, TimeUnit.SECONDS)) {
                return TestParserListener.responses.remove(correlationId);
            }
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }
}
