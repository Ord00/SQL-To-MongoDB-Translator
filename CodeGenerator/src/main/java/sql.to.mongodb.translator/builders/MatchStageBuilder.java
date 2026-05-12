package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.code.generator.TranslationResult;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.translators.ConditionTranslator;

import static sql.to.mongodb.translator.helpers.FormatHelper.indent;

@Component
public class MatchStageBuilder {

    private final ConditionTranslator conditionTranslator;

    public MatchStageBuilder(ConditionTranslator conditionTranslator) {
        this.conditionTranslator = conditionTranslator;
    }

    public TranslationResult buildWhere(SqlToMongoIR ir,
                                        GenerationContext context) throws CodeGenerationException {
        if (ir.getWhereCondition() == null) {
            return TranslationResult.empty();
        }

        TranslationResult result = translateCondition(ir.getWhereCondition(), context, true);
        String condition = result.getCondition();
        condition = wrapAggregationCondition(condition, context);
        if (condition != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent(context)).append("{\n");
            context.increaseIndent();
            sb.append(indent(context)).append("$match: ").append((condition));
            context.decreaseIndent();
            sb.append(indent(context)).append("}");
            result.setCondition(sb.toString());
        }
        return result;
    }

    public TranslationResult buildHaving(SqlToMongoIR ir,
                                         GenerationContext context) throws CodeGenerationException {
        if (ir.getHavingCondition() == null) {
            return TranslationResult.empty();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(indent(context)).append("{\n");
        context.increaseIndent();
        TranslationResult result = translateCondition(ir.getHavingCondition(), context, true);
        String condition = result.getCondition();
        sb.append(indent(context)).append("$match: ").append((condition));
        context.decreaseIndent();
        sb.append(indent(context)).append("}");
        result.setCondition(sb.toString());
        return result;
    }

    private TranslationResult translateCondition(ConditionNode conditionNode,
                                                 GenerationContext context,
                                                 boolean useAggregationSyntax) throws CodeGenerationException {
        boolean originalSyntax = context.isUseAggregationSyntax();
        context.setUseAggregationSyntax(useAggregationSyntax);
        try {
            return conditionTranslator.translate(conditionNode, context);
        } finally {
            context.setUseAggregationSyntax(originalSyntax);
        }
    }

    public String buildFindCondition(SqlToMongoIR ir,
                                     GenerationContext context) throws CodeGenerationException {
        if (ir.getWhereCondition() == null) {
            return null;
        }
        TranslationResult result = translateCondition(ir.getWhereCondition(), context, false);
        String condition = result.getCondition();
        if (condition != null && condition.startsWith("{ $")) {
            return "{ $expr: " + condition + " }";
        }
        return condition;
    }

    private String wrapAggregationCondition(String condition,
                                            GenerationContext context) {
        if (condition == null || condition.isBlank()) {
            return condition;
        }
        if (condition.contains("\n")) {
            String exprBody = condition;
            if (condition.startsWith("{\n") && condition.endsWith("\n}")) {
                exprBody = condition.substring(2, condition.length() - 2);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (int i = 1; i < 3; i++) {
                context.increaseIndent();
            }
            sb.append(indent(context)).append("$expr: {\n");
            sb.append(indentMultiline(exprBody, context)).append("\n");
            sb.append(indent(context)).append("}\n");
            context.decreaseIndent();
            sb.append(indent(context)).append("}\n");
            context.decreaseIndent();
            return sb.toString();
        }
        return "{ $expr: " + condition + " }";
    }

    private String indentMultiline(String input, GenerationContext context) {
        String[] lines = input.split("\\R", -1);
        if (lines.length <= 1) {
            return input;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            out.append(indent(context)).append(lines[i]);
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        return out.toString();
    }
}
