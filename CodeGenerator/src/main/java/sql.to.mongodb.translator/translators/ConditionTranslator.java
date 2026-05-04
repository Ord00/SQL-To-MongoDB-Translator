package sql.to.mongodb.translator.translators;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.condition.BetweenCondition;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.ExistsCondition;
import sql.to.mongodb.translator.ir.condition.InCondition;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.ir.condition.NullCheck;
import sql.to.mongodb.translator.ir.expression.Expressionable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConditionTranslator {

    private final ExpressionTranslator expressionTranslator;
    private final SubqueryHelper subqueryHelper;

    private static final Map<String, String> OPERATOR_MAP = Map.of(
            "=", "$eq", "!=", "$ne", "<>", "$ne",
            "<", "$lt", "<=", "$lte", ">", "$gt", ">=", "$gte",
            "LIKE", "$regex"
    );

    private static final Map<String, String> OPERATOR_REVERSE_MAP = Map.of(
            "=", "$eq", "!=", "$ne", "<>", "$ne",
            "<", "$gt", "<=", "$gte", ">", "$lt", ">=", "$lte",
            "LIKE", "$regex"
    );

    public ConditionTranslator(ExpressionTranslator expressionTranslator,
                               SubqueryHelper subqueryHelper) {
        this.expressionTranslator = expressionTranslator;
        this.subqueryHelper = subqueryHelper;
    }

    public String translate(ConditionNode condition,
                            GenerationContext context) throws CodeGenerationException {
        return switch (condition) {
            case LinkNode link -> translateLink(link, context);
            case Comparison comp -> translateComparison(comp, context);
            case BetweenCondition between -> translateBetween(between, context);
            case InCondition in -> translateIn(in, context);
            case NullCheck nullCheck -> translateNullCheck(nullCheck, context);
            case ExistsCondition exists -> translateExists(exists, context);
            case null, default -> "{}";
        };

    }

    private String translateLink(LinkNode link, GenerationContext context) {
        List<String> parts = link.getChildren().stream()
                .map(c -> {
                    try {
                        return translate(c, context);
                    } catch (Exception e) {
                        return "{}";
                    }
                })
                .filter(s -> !s.isEmpty() && !"{}".equals(s))
                .collect(Collectors.toList());

        if (parts.isEmpty()) return "{}";
        if (parts.size() == 1) return parts.getFirst();

        String op = link.getType() == LinkNode.LinkType.AND ? "$and" : "$or";
        return "{ " + op + ": [ " + String.join(", ", parts) + " ] }";
    }

    //TODO агрегация при сравнении переменной с переменной (возможно в IRGenerator'е)
    private String translateComparison(Comparison comp,
                                       GenerationContext context) throws CodeGenerationException {

        String field;
        String value;
        String mongoOp;

        if (comp.getOperand() instanceof Constant) {
            if (comp.getValue() instanceof Constant) {
                throw new CodeGenerationException("Comparison of 2 constants!");
            }
            value = expressionTranslator.translate(comp.getOperand(), context);
            field = expressionTranslator.translate(comp.getValue(), context);
            mongoOp = OPERATOR_REVERSE_MAP.getOrDefault(comp.getOperator(), "$eq");
        } else {
            field = expressionTranslator.translate(comp.getOperand(), context);
            value = expressionTranslator.translate(comp.getValue(), context);
            mongoOp = OPERATOR_MAP.getOrDefault(comp.getOperator(), "$eq");
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { " + mongoOp + ": [ " + field + ", " + value + " ] } }";
        }
        return "$eq".equals(mongoOp) ? "{ " + field + ": " + value + " }"
                : "{ " + field + ": { " + mongoOp + ": " + value + " } }";
    }

    private String translateBetween(BetweenCondition between,
                                    GenerationContext context) throws CodeGenerationException {
        String field = expressionTranslator.translate(between.getOperand(), context);
        String start = between.getStart() != null ? between.getStart().toString() : "null";
        String end = between.getEnd() != null ? between.getEnd().toString() : "null";

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $and: [ { $gte: [ " + field + ", " + start + " ] }, { $lte: [ " + field + ", " + end + " ] } ] } }";
        }
        return "{ " + field + ": { $gte: " + start + ", $lte: " + end + " } }";
    }

    private String translateIn(InCondition in,
                               GenerationContext context) throws CodeGenerationException {
        String field = expressionTranslator.translate(in.getOperand(), context);
        List<String> values = new java.util.ArrayList<>();

        for (Expressionable expr : in.getInValues()) {
            if (expr instanceof Subquery subquery) {
                return translateInWithSubquery(field, subquery, context);
            }
            values.add(expressionTranslator.translate(expr, context));
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $in: [ " + field + ", [ " + String.join(", ", values) + " ] ] } }";
        }
        return "{ " + field + ": { $in: [ " + String.join(", ", values) + " ] } }";
    }

    private String translateInWithSubquery(String field,
                                           Subquery subquery,
                                           GenerationContext context) {
        boolean correlated = subqueryHelper.isCorrelated(subquery);

        if (correlated) {
            String corrName = context.nextCorrelationName();
            return "{ $expr: { $in: [ " + field + ", \"$" + corrName + "\" ] } }";
        }
        return "{ " + field + ": { $in: /* subquery result */ } }";
    }

    private String translateNullCheck(NullCheck nullCheck,
                                      GenerationContext context) throws CodeGenerationException {
        String field = expressionTranslator.translate(nullCheck.getOperand(), context);
        boolean isNull = nullCheck.isNull();

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { " + (isNull ? "$eq" : "$ne") + ": [ " + field + ", null ] } }";
        }
        return "{ " + field + ": " + (isNull ? "null" : "{ $ne: null }") + " }";
    }

    private String translateExists(ExistsCondition exists,
                                   GenerationContext context) {
        CorrelationSubquery subquery = exists.getSubquery();
        if (subquery == null) return "{ $exists: " + exists.isExists() + " }";

        boolean correlated = subqueryHelper.isCorrelated(subquery);

        if (correlated) {
            String corrName = context.nextCorrelationName();
            if (context.isUseAggregationSyntax()) {
                return "{ $expr: { " + (exists.isExists() ? "$gt" : "$eq") +
                        ": [ { $size: \"$" + corrName + "\" }, 0 ] } }";
            }
            return "{ $where: \"this." + corrName + ".length " + (exists.isExists() ? ">" : "==") + " 0\" }";
        }
        return "{ $where: \"/* subquery result */.length " + (exists.isExists() ? ">" : "==") + " 0\" }";
    }
}
