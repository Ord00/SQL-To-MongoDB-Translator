package sql.to.mongodb.translator.listener;

import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.CodeGenerator;
import sql.to.mongodb.translator.responses.AnalysisResult;
import sql.to.mongodb.translator.requests.CodeGeneratorRequest;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.ir.SqlToMongoIR;

@Component
@AllArgsConstructor
public class IRGeneratorListener {

    private final RabbitTemplate rabbitTemplate;

    private CodeGenerator codeGenerator;

    @RabbitListener(queues = "${rabbitmq.request.queue.name}")
    public void listenIRGeneration(CodeGeneratorRequest request,
                                   @Header("amqp_correlationId") String correlationId,
                                   @Header("amqp_replyTo") String replyTo) {
        SqlToMongoIR ir = request.ir();

        try {
            String mqlCode = codeGenerator.generate(ir);
            AnalysisResult analysisResult = AnalysisResult.success(
                    request.lexicalResult(),
                    request.syntaxResult(),
                    request.ir(),
                    mqlCode);
            rabbitTemplate.convertAndSend(replyTo, analysisResult, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                return m;
            });
        } catch (CodeGenerationException e) {
            AnalysisResult analysisResult = AnalysisResult.error(e.getMessage(), 401);
            rabbitTemplate.convertAndSend(replyTo, analysisResult, m -> {
                m.getMessageProperties().setCorrelationId(correlationId);
                return m;
            });
        }
    }
}
