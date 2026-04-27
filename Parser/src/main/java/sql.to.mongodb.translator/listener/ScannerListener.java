package sql.to.mongodb.translator.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.Parser;
import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.requests.IRGeneratorRequest;
import sql.to.mongodb.translator.requests.ParserRequest;
import sql.to.mongodb.translator.responses.AnalysisResult;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

@Component
public class ScannerListener {
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.response.error.queue.name}")
    private String errorQueue;

    @Value("${rabbitmq.response.success.queue.name}")
    private String successQueue;

    private final Parser parser;

    public ScannerListener(RabbitTemplate rabbitTemplate, Parser parser) {
        this.rabbitTemplate = rabbitTemplate;
        this.parser = parser;
    }

    @RabbitListener(queues = "${rabbitmq.request.queue.name}")
    public void listenIRGeneration(ParserRequest request,
                                   @Header("amqp_correlationId") String correlationId,
                                   @Header("amqp_replyTo") String replyTo) {
        List<Token> lexicalResult = request.lexicalResult();

        try {
            Node syntaxResult = parser.tryAnalyse(lexicalResult);
            IRGeneratorRequest irGeneratorRequest = new IRGeneratorRequest(lexicalResult, syntaxResult);
            rabbitTemplate.convertAndSend(successQueue, irGeneratorRequest, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                m.getMessageProperties().setReplyTo(replyTo);
                return m;
            });
        } catch (SQLParseException e) {
            AnalysisResult analysisResult = AnalysisResult.error(e.getMessage(), 401);
            rabbitTemplate.convertAndSend(errorQueue, analysisResult, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                return m;
            });
        }
    }
}
