package sql.to.mongodb.translator.builders;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.code.generator.CorrelationVariable;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        // установить состояние с алиасами

        boolean originalSyntax = context.isUseAggregationSyntax();
        context.setUseAggregationSyntax(true);
        context.setInsideSubquery(true);
        context.enterSubquery();

        try {
            subIR.getProjectionFields().clear();
            for (int i = 0; i < 3; ++i) {
                context.increaseIndent();
            }

            // до построения стадий обновить список своих переменных в соответствии с внешним запросом
            // после построения стадий второй раз проверить свои корреляции, чтобы обновить контекст корреляций
            Map<String, String> aliases = context.peekAliases();

            List<CorrelationCondition> outerCorr = new ArrayList<>();

            List<CorrelationCondition> correlations = subquery.getCorrelations();
            if (correlations != null && !correlations.isEmpty()) {
                for (CorrelationCondition corr : correlations) {

                    if (aliases.containsKey(corr.getOuterField().getSource())) {
                        Field outer =  corr.getOuterField();
                        String outerField = context.resolveFieldPath(outer.getSource(), outer.getField());
                        String varName = corr.getOuterField().getField().toLowerCase();
                        context.addCorrelationVariable(outer.getSource() + "." + outer.getField(),
                                new CorrelationVariable(varName, outerField));
                    } else {
                        Field outer =  corr.getOuterField();
                        String outerField = outer.getField().toLowerCase();

                        outerCorr.add(corr);
                        String varName = "outer_" + outerField;
                        context.addCorrelationVariable(outer.getSource() + "." + outer.getField(),
                                new CorrelationVariable(varName, "$" + outerField));
                    }
                }
            }

            // Строим pipeline для подзапроса
            List<String> pipelineStages = pipelineBuilder.buildStages(subIR, context);

            List<CorrelationCondition> newContextCorr = context.getCorrelationConditions();

            for (CorrelationCondition corr : context.getCorrelationConditions()) {
                if (aliases.containsKey(corr.getOuterField().getSource())) {
                    Field outer =  corr.getOuterField();
                    String outerField = context.resolveFieldPath(outer.getSource(), outer.getField());
                    String varName = corr.getOuterField().getField().toLowerCase();
                    context.addCorrelationVariable(outer.getSource() + "." + outer.getField(),
                            new CorrelationVariable(varName, outerField));
                } else {
                    newContextCorr.add(corr);
                }
            }

            context.setCorrelationConditions(newContextCorr);

            // Создаём $lookup
            String lookup = lookupStageBuilder.buildLookupWithPipeline(
                    subIR.getMainCollection(),
                    subqueryName,
                    pipelineStages,
                    context
            );
            stages.add(lookup);

            // Добавляем $match для EXISTS/NOT EXISTS
            String match = buildExistsMatch(subqueryName, isExists, context);
            stages.add(match);

            if (correlations != null && !correlations.isEmpty()) {
                for (CorrelationCondition corr : correlations) {
                    if (outerCorr.contains(corr)) {
                        context.addCorrelation(corr);
                    }
                }
            }

        } finally {
            context.setInsideSubquery(false);
            context.setUseAggregationSyntax(originalSyntax);
            // сбросить состояние с алиасами
            context.popAliases();
            context.leaveSubquery();
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