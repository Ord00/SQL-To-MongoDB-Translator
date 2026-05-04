package sql.to.mongodb.translator.helper;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.CodeGenerator;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.listener.TestIRGeneratorListener;
import sql.to.mongodb.translator.requests.ScannerRequest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@Profile("test")
public class RabbitMQTestHelper {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CodeGenerator codeGenerator;

    private static final String SCANNER_QUEUE = "scanner_queue";
    private static final String CODE_GENERATOR_QUEUE = "code_generator_queue";

    public String getCodeGeneratorResult(String sql) throws CodeGenerationException {
        ScannerRequest request = new ScannerRequest(sql);
        String correlationId = UUID.randomUUID().toString();

        CountDownLatch latch = new CountDownLatch(1);
        TestIRGeneratorListener.latches.put(correlationId, latch);

        rabbitTemplate.convertAndSend(SCANNER_QUEUE, request, message -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setReplyTo(CODE_GENERATOR_QUEUE);
            message.getMessageProperties().setContentType("application/json");
            return message;
        });

        try {
            if (latch.await(10, TimeUnit.SECONDS)) {
                SqlToMongoIR sqlToMongoIR = TestIRGeneratorListener.responses.remove(correlationId);
                return codeGenerator.generate(sqlToMongoIR);
            }
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }
}
