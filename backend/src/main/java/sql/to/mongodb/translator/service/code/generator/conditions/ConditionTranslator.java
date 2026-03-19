package sql.to.mongodb.translator.service.code.generator.conditions;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConditionTranslator {

    private final ComparisonTranslator comparisonTranslator;

    public ConditionTranslator(ComparisonTranslator comparisonTranslator) {
        this.comparisonTranslator = comparisonTranslator;
    }

    public String translate(ConditionNode node,
                            GenerationContext context) throws CodeGenerationException {

        if (node == null) return "{}";

        return switch (node.getType()) {
            case AND, OR -> translateLogical(node, context);
            case COMPARISON -> comparisonTranslator.translate(node, context);
            case IS_NULL, IS_NOT_NULL -> comparisonTranslator.translateIsNull(node, context);
            case BETWEEN -> comparisonTranslator.translateBetween(node, context);
            case IN -> comparisonTranslator.translateIn(node, context);
            case EXISTS, NOT_EXISTS -> comparisonTranslator.translateExists(node, context);
            default -> "{}";
        };
    }

    private String translateLogical(ConditionNode node,
                                    GenerationContext context) throws CodeGenerationException {
        List<String> parts = new ArrayList<>();
        for (ConditionNode child : node.getChildren()) {
            String childCond = translate(child, context);
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
