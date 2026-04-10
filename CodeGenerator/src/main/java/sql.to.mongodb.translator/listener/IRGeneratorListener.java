package sql.to.mongodb.translator.listener;

import lombok.AllArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.CodeGenerator;
import sql.to.mongodb.translator.responses.AnalysisResult;
import sql.to.mongodb.translator.requests.CodeGeneratorRequest;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.ir.SqlToMongoIR;

import static org.springframework.amqp.utils.SerializationUtils.deserialize;

@Component
@AllArgsConstructor
public class IRGeneratorListener {

    private final RabbitTemplate rabbitTemplate;

    private CodeGenerator codeGenerator;

    @RabbitListener(queues = "${rabbitmq.request.queue.name}")
    public void listenIRGeneration(@Payload Message message) {
        CodeGeneratorRequest request = (CodeGeneratorRequest) deserialize(message.getBody());
        SqlToMongoIR ir = request.ir();

        String correlationId = message.getMessageProperties().getCorrelationId();
        String replyTo = message.getMessageProperties().getReplyTo();

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
