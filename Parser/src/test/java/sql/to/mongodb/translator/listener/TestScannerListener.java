package sql.to.mongodb.translator.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.requests.ParserRequest;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

@Component
@Profile("test")
public class TestScannerListener {

    public static final Map<String, List<Token>> responses = new ConcurrentHashMap<>();
    public static final Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();

    @RabbitListener(queues = "${rabbitmq.request.queue.name}")
    public void listenIRGeneration(ParserRequest request,
                                   @Header("amqp_correlationId") String correlationId) {
        CountDownLatch latch = latches.remove(correlationId);
        if (latch != null) {
            responses.put(correlationId, request.lexicalResult());
            latch.countDown();
        }
    }
}
