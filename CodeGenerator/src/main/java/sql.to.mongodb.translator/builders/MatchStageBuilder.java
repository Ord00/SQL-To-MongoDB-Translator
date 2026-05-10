package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.translators.ConditionTranslator;

import java.util.List;

@Component
public class MatchStageBuilder {

    private final ConditionTranslator conditionTranslator;

    public MatchStageBuilder(ConditionTranslator conditionTranslator) {
        this.conditionTranslator = conditionTranslator;
    }

    public String buildWhere(SqlToMongoIR ir,
                             GenerationContext context) throws CodeGenerationException {
        if (ir.getWhereCondition() == null) {
            return null;
        }
        String condition = translateCondition(ir.getWhereCondition(), context, true);
        if (condition == null || condition.isBlank() || "{}".equals(condition)) {
            return null;
        }
        condition = wrapAggregationCondition(condition, true);
        return indent(context) + "{\n"
                + indent(context) + "    $match: " + condition + "\n"
                + indent(context) + "}";
    }

    public String buildHaving(SqlToMongoIR ir,
                              GenerationContext context) throws CodeGenerationException {
        if (ir.getHavingCondition() == null) {
            return null;
        }
        String condition = translateCondition(ir.getHavingCondition(), context, true);
        condition = wrapAggregationCondition(condition, true);
        return indent(context) + "{\n"
                + indent(context) + "    $match: " + condition + "\n"
                + indent(context) + "}";
    }

    public String buildFindCondition(SqlToMongoIR ir,
                                     GenerationContext context) throws CodeGenerationException {
        if (ir.getWhereCondition() == null) {
            return null;
        }
        String condition = translateCondition(ir.getWhereCondition(), context, false);
        if (condition != null && condition.startsWith("{ $")) {
            return "{ $expr: " + condition + " }";
        }
        return condition;
    }

    private ConditionNode combineConditions(List<ConditionNode> conditions) {
        if (conditions.size() == 1) return conditions.getFirst();
        LinkNode root = new LinkNode();
        root.setType(LinkNode.LinkType.AND);
        root.getChildren().addAll(conditions);
        return root;
    }

    private String translateCondition(ConditionNode conditionNode,
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

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }

    private String wrapAggregationCondition(String condition, boolean useAggregationSyntax) {
        if (!useAggregationSyntax || condition == null || condition.isBlank()) {
            return condition;
        }
        if (condition.contains("\n")) {
            String exprBody = condition;
            if (condition.startsWith("{\n") && condition.endsWith("\n}")) {
                exprBody = condition.substring(2, condition.length() - 2);
            }
            return "{\n"
                    + "            $expr: {\n"
                    + indentMultiline(exprBody, "            ") + "\n"
                    + "            }\n"
                    + "        }";
        }
        return "{ $expr: " + condition + " }";
    }

    private String indentMultiline(String input, String indent) {
        String[] lines = input.split("\\R", -1);
        if (lines.length <= 1) {
            return input;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            out.append(indent).append(lines[i]);
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        return out.toString();
    }
}
