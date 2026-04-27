package sql.to.mongodb.translator.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.IRGenerator;
import sql.to.mongodb.translator.exceptions.IRGenerationException;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.requests.CodeGeneratorRequest;
import sql.to.mongodb.translator.requests.IRGeneratorRequest;
import sql.to.mongodb.translator.responses.AnalysisResult;

@Component
public class ParserListener {
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.response.error.queue.name}")
    private String errorQueue;

    @Value("${rabbitmq.response.success.queue.name}")
    private String successQueue;

    private final IRGenerator generator;

    public ParserListener(RabbitTemplate rabbitTemplate, IRGenerator generator) {
        this.rabbitTemplate = rabbitTemplate;
        this.generator = generator;
    }

    @RabbitListener(queues = "${rabbitmq.request.queue.name}")
    public void listenParser(IRGeneratorRequest request,
                             @Header("amqp_correlationId") String correlationId,
                             @Header("amqp_replyTo") String replyTo) {
        Node syntaxResult = request.syntaxResult();

        try {
            SqlToMongoIR ir = generator.generateIR(syntaxResult);
            CodeGeneratorRequest codeGeneratorRequest = new CodeGeneratorRequest(
                    request.lexicalResult(),
                    request.syntaxResult(),
                    ir);
            rabbitTemplate.convertAndSend(successQueue, codeGeneratorRequest, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                m.getMessageProperties().setReplyTo(replyTo);
                return m;
            });
        } catch (IRGenerationException e) {
            AnalysisResult analysisResult = AnalysisResult.error(e.getMessage(), 401);
            rabbitTemplate.convertAndSend(errorQueue, analysisResult, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                return m;
            });
        }
    }
}
