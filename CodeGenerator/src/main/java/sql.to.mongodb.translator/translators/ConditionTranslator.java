package sql.to.mongodb.translator.translators;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.builders.ExistsSubqueryBuilder;
import sql.to.mongodb.translator.builders.InSubqueryBuilder;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.condition.BetweenCondition;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.ExistsCondition;
import sql.to.mongodb.translator.ir.condition.InCondition;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.ir.condition.NullCheck;
import sql.to.mongodb.translator.ir.expression.Expressionable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConditionTranslator {

    private final ExpressionTranslator expressionTranslator;
    private final InSubqueryBuilder inSubqueryBuilder;
    private final ExistsSubqueryBuilder existsSubqueryBuilder;

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
                               @Lazy InSubqueryBuilder inSubqueryBuilder,
                               @Lazy ExistsSubqueryBuilder existsSubqueryBuilder) {
        this.expressionTranslator = expressionTranslator;
        this.inSubqueryBuilder = inSubqueryBuilder;
        this.existsSubqueryBuilder = existsSubqueryBuilder;
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
        if (context.isUseAggregationSyntax()) {
            StringBuilder result = new StringBuilder("{\n");
            result.append("    ").append(op).append(": [\n");
            for (int i = 0; i < parts.size(); i++) {
                result.append(indentBlock(parts.get(i), "        "));
                if (i < parts.size() - 1) {
                    result.append(",");
                }
                result.append("\n");
            }
            result.append("    ]\n");
            result.append("}");
            return result.toString();
        }
        return "{ " + op + ": [ " + String.join(", ", parts) + " ] }";
    }

    private String translateComparison(Comparison comp,
                                       GenerationContext context) throws CodeGenerationException {

        String field;
        String value;
        String mongoOp;

        boolean isExpr = false;

        if (comp.getOperand() instanceof Constant) {
            if (comp.getValue() instanceof Constant) {
                throw new CodeGenerationException("Comparison of 2 constants!");
            }
            value = expressionTranslator.translate(comp.getOperand(), context);
            field = expressionTranslator.translate(comp.getValue(), context);
            mongoOp = OPERATOR_REVERSE_MAP.getOrDefault(comp.getOperator(), "$eq");
        } else {
            if (comp.getValue() instanceof Field && !context.isUseAggregationSyntax()) {
                context.setUseAggregationSyntax(true);
                isExpr = true;
            }
            field = expressionTranslator.translate(comp.getOperand(), context);
            value = expressionTranslator.translate(comp.getValue(), context);
            mongoOp = OPERATOR_MAP.getOrDefault(comp.getOperator(), "$eq");
        }

        if (context.isUseAggregationSyntax()) {
            if (isExpr) {
                context.setUseAggregationSyntax(false);
                return "{ " + mongoOp + ": [ " + field + ", " + value + " ] }";
            }
            return "{ " + mongoOp + ": [" + field + ", " + value + "] }";
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
            return "{ $and: [{ $gte: [" + field + ", " + start + "] }, { $lte: [" + field + ", " + end + "] }] }";
        }
        return "{ " + field + ": { $gte: " + start + ", $lte: " + end + " } }";
    }

    private String translateIn(InCondition in,
                               GenerationContext context) throws CodeGenerationException {
        String field = expressionTranslator.translate(in.getOperand(), context);
        List<String> values = new ArrayList<>();

        for (Expressionable expr : in.getInValues()) {
            if (expr instanceof Subquery) {
                var result = inSubqueryBuilder.build(in, context);
                if (result != null) {
                    context.addStages(result.stages());
                    return "{\n"
                            + "    $in: [\n"
                            + "        " + field + ",\n"
                            + "        " + result.arrayPath() + "\n"
                            + "    ]\n"
                            + "}";
                }
            }
            values.add(expressionTranslator.translate(expr, context));
        }

        if (context.isUseAggregationSyntax()) {
            return "{\n"
                    + "    $in: [\n"
                    + "        " + field + ",\n"
                    + "        [" + String.join(", ", values) + "]\n"
                    + "    ]\n"
                    + "}";
        }
        return "{ " + field + ": { $in: [ " + String.join(", ", values) + " ] } }";
    }

    private String translateNullCheck(NullCheck nullCheck,
                                      GenerationContext context) throws CodeGenerationException {
        String field = expressionTranslator.translate(nullCheck.getOperand(), context);
        boolean isNull = nullCheck.isNull();

        if (context.isUseAggregationSyntax()) {
            return "{ " + (isNull ? "$eq" : "$ne") + ": [" + field + ", null] }";
        }
        return "{ " + field + ": " + (isNull ? "null" : "{ $ne: null }") + " }";
    }

    private String translateExists(ExistsCondition exists,
                                   GenerationContext context) throws CodeGenerationException {
        CorrelationSubquery subquery = exists.getSubquery();
        if (subquery == null) return "{ $exists: " + exists.isExists() + " }";

        // Для всех подзапросов (и коррелированных, и нет) генерируем стадии
        var result = existsSubqueryBuilder.build(subquery, exists.isExists(), context);
        if (result != null) {
            context.addStages(result.stages());
        }

        // Возвращаем условие, которое всегда true (фильтрация уже сделана в $match)
        return "{}";
    }

    private String indentBlock(String input, String indent) {
        String[] lines = input.split("\\R", -1);
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
