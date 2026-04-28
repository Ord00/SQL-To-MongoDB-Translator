package sql.to.mongodb.translator.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import sql.to.mongodb.translator.requests.ScannerRequest;
import sql.to.mongodb.translator.responses.AnalysisResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TranslateService {
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.response.queue.name}")
    private String responseQueue;

    @Value("${rabbitmq.scanner.queue.name}")
    private String scannerQueue;

    private final Map<String, CompletableFuture<AnalysisResult>> pendingRequests = new ConcurrentHashMap<>();

    public TranslateService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<AnalysisResult> translate(ScannerRequest request) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<AnalysisResult> future = new CompletableFuture<>();
        pendingRequests.put(correlationId, future);

        rabbitTemplate.convertAndSend(scannerQueue, request, message -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setReplyTo(responseQueue);
            return message;
        });

        return future;
    }

    @RabbitListener(queues = "${rabbitmq.response.queue.name}")
    public void onResponse(AnalysisResult result,
                           @Header("amqp_correlationId") String correlationId) {
        CompletableFuture<AnalysisResult> future = pendingRequests.remove(correlationId);
        if (future != null) {
            future.complete(result);
        }
    }
}
