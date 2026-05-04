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
        return indent(context) + "{ $match: " + condition + " }";
    }

    public String buildHaving(SqlToMongoIR ir,
                              GenerationContext context) throws CodeGenerationException {
        if (ir.getHavingCondition() == null) {
            return null;
        }
        String condition = translateCondition(ir.getHavingCondition(), context, true);
        return indent(context) + "{ $match: " + condition + " }";
    }

    public String buildJoinMatch(List<ConditionNode> conditions,
                                 GenerationContext context) throws CodeGenerationException {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        ConditionNode root = combineConditions(conditions);
        String condition = translateCondition(root, context, true);
        return indent(context) + "{ $match: " + condition + " }";
    }

    public String buildFindCondition(SqlToMongoIR ir,
                                     GenerationContext context) throws CodeGenerationException {
        if (ir.getWhereCondition() == null) {
            return null;
        }
        return translateCondition(ir.getWhereCondition(), context, false);
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
        return "  ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
