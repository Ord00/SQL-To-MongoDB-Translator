package sql.to.mongodb.translator.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.Scanner;
import sql.to.mongodb.translator.exceptions.SQLScanException;
import sql.to.mongodb.translator.requests.ParserRequest;
import sql.to.mongodb.translator.requests.ScannerRequest;
import sql.to.mongodb.translator.responses.AnalysisResult;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

@Component
public class GatewayListener {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.response.error.queue.name}")
    private String errorQueue;

    @Value("${rabbitmq.response.success.queue.name}")
    private String successQueue;

    private final Scanner scanner;

    public GatewayListener(RabbitTemplate rabbitTemplate, Scanner scanner) {
        this.rabbitTemplate = rabbitTemplate;
        this.scanner = scanner;
    }

    @RabbitListener(queues = "${rabbitmq.request.queue.name}")
    public void listenIRGeneration(ScannerRequest request,
                                   @Header("amqp_correlationId") String correlationId,
                                   @Header("amqp_replyTo") String replyTo) {
        String sqlCode = request.sqlQuery();

        try {
            List<Token> lexicalResult = new ArrayList<>();
            scanner.tryAnalyse(sqlCode, lexicalResult);
            ParserRequest parserRequest = new ParserRequest(lexicalResult);
            rabbitTemplate.convertAndSend(successQueue, parserRequest, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                m.getMessageProperties().setReplyTo(replyTo);
                return m;
            });
        } catch (SQLScanException e) {
            AnalysisResult analysisResult = AnalysisResult.error(e.getMessage(), 401);
            rabbitTemplate.convertAndSend(errorQueue, analysisResult, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                return m;
            });
        }
    }
}
