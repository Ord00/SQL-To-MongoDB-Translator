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
            for (int i = 0; i < 3; ++i) {
                context.increaseIndent();
            }

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
        StringBuilder sb = new StringBuilder();

        if (isExists) {
            sb.append(indent(context)).append("{\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$match: {\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$expr: {\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$gt: [\n");
            context.increaseIndent();
            sb.append(indent(context)).append("{ $size: \"$").append(arrayName).append("\" },\n");
            sb.append(indent(context)).append("0\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("]\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}");

            return sb.toString();
        } else {
            sb.append(indent(context)).append("{\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$match: {\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$expr: {\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$eq: [\n");
            context.increaseIndent();
            sb.append(indent(context)).append("{ $size: \"$").append(arrayName).append("\" },\n");
            sb.append(indent(context)).append("0\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("]\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}");

            return sb.toString();
        }
    }

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}