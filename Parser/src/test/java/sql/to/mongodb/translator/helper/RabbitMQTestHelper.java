package sql.to.mongodb.translator.helper;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.listener.TestScannerListener;
import sql.to.mongodb.translator.requests.ScannerRequest;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@Profile("test")
public class RabbitMQTestHelper {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String SCANNER_QUEUE = "scanner_queue";
    private static final String PARSER_QUEUE = "parser_queue";

    public List<Token> getScannerResult(String sql) {
        ScannerRequest request = new ScannerRequest(sql);
        String correlationId = UUID.randomUUID().toString();

        CountDownLatch latch = new CountDownLatch(1);
        TestScannerListener.latches.put(correlationId, latch);

        rabbitTemplate.convertAndSend(SCANNER_QUEUE, request, message -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setReplyTo(PARSER_QUEUE);
            message.getMessageProperties().setContentType("application/json");
            return message;
        });

        try {
            if (latch.await(10, TimeUnit.SECONDS)) {
                return TestScannerListener.responses.remove(correlationId);
            }
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }
}
