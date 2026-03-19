package sql.to.mongodb.translator.service.code.generator.conditions;

import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;

import java.util.ArrayList;
import java.util.List;

public class ConditionTranslator extends BaseGenerator {

    private final ComparisonTranslator comparisonTranslator;

    public ConditionTranslator(SqlToMongoIR ir, GenerationContext context) {
        super(ir, context);
        this.comparisonTranslator = new ComparisonTranslator(ir, context);
    }

    @Override
    public String generate() throws CodeGenerationException {
        return ""; // Не используется напрямую
    }

    public String translate(ConditionNode node) throws CodeGenerationException {
        if (node == null) return "{}";

        return switch (node.getType()) {
            case AND, OR -> translateLogical(node);
            case COMPARISON -> comparisonTranslator.translate(node);
            case IS_NULL, IS_NOT_NULL -> comparisonTranslator.translateIsNull(node);
            case BETWEEN -> comparisonTranslator.translateBetween(node);
            case IN -> comparisonTranslator.translateIn(node);
            case EXISTS, NOT_EXISTS -> comparisonTranslator.translateExists(node);
            default -> "{}";
        };
    }

    private String translateLogical(ConditionNode node) throws CodeGenerationException {
        List<String> parts = new ArrayList<>();
        for (ConditionNode child : node.getChildren()) {
            String childCond = translate(child);
            if (!childCond.isEmpty() && !childCond.equals("{}")) {
                parts.add(childCond);
            }
        }

        if (parts.isEmpty()) return "{}";
        if (parts.size() == 1) return parts.getFirst();

        String op = node.getType() == ConditionNode.ConditionType.AND ? "$and" : "$or";
        return "{ " + op + ": [ " + String.join(", ", parts) + " ] }";
    }

    public static ConditionNode getConditionNode(List<ConditionNode> conditions) {
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.getFirst();

        ConditionNode root = new ConditionNode();
        root.setType(ConditionNode.ConditionType.AND);
        root.getChildren().addAll(conditions);
        return root;
    }
}
