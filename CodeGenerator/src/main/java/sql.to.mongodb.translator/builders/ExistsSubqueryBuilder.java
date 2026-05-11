package sql.to.mongodb.translator.builders;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExistsSubqueryBuilder {

    private final PipelineBuilder pipelineBuilder;
    private final LookupStageBuilder lookupStageBuilder;

    public ExistsSubqueryBuilder(@Lazy PipelineBuilder pipelineBuilder,
                                 LookupStageBuilder lookupStageBuilder) {
        this.pipelineBuilder = pipelineBuilder;
        this.lookupStageBuilder = lookupStageBuilder;
    }

    public record ExistsSubqueryResult(List<String> stages, String arrayName) { }

    public ExistsSubqueryResult build(CorrelationSubquery subquery,
                                      boolean isExists,
                                      GenerationContext context) throws CodeGenerationException {

        SqlToMongoIR subIR = subquery.getSubqueryIR();
        String subqueryName = context.nextSubqueryName();

        List<String> stages = new ArrayList<>();

        boolean originalSyntax = context.isUseAggregationSyntax();
        context.setUseAggregationSyntax(true);
        context.setInsideSubquery(true);
        context.enterSubquery();

        try {
            subIR.getProjectionFields().clear();

            // Строим pipeline для подзапроса
            List<String> pipelineStages = pipelineBuilder.buildStages(subIR, context);

            List<CorrelationCondition> correlations = subquery.getCorrelations();
            if (correlations != null && !correlations.isEmpty()) {
                String correlationMatch = lookupStageBuilder.buildCorrelationMatch(correlations, context);
                if (correlationMatch != null) {
                    pipelineStages.addFirst(correlationMatch);
                }
            }

            // Создаём $lookup
            String lookup = lookupStageBuilder.buildLookupWithPipeline(
                    subIR.getMainCollection(),
                    subqueryName,
                    pipelineStages,
                    subquery.getCorrelations(),
                    context
            );
            stages.add(lookup);

            // Добавляем $match для EXISTS/NOT EXISTS
            String match = buildExistsMatch(subqueryName, isExists, context);
            stages.add(match);

        } finally {
            context.setInsideSubquery(false);
            context.setUseAggregationSyntax(originalSyntax);
        }

        return new ExistsSubqueryResult(stages, subqueryName);
    }

    private String buildExistsMatch(String arrayName, boolean isExists, GenerationContext context) {
        String indent = context.getIndent();
        if (isExists) {
            return indent + "{\n"
                    + indent + "    $match: {\n"
                    + indent + "        $expr: { $gt: [ { $size: \"$" + arrayName + "\" }, 0 ] }\n"
                    + indent + "    }\n"
                    + indent + "}";
        } else {
            return indent + "{\n"
                    + indent + "    $match: {\n"
                    + indent + "        $expr: { $eq: [ { $size: \"$" + arrayName + "\" }, 0 ] }\n"
                    + indent + "    }\n"
                    + indent + "}";
        }
    }
}