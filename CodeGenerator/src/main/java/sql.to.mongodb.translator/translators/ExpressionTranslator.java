package sql.to.mongodb.translator.translators;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.helpers.FormatHelper;
import sql.to.mongodb.translator.ir.Aggregate;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.expression.BinaryOperation;
import sql.to.mongodb.translator.ir.expression.CaseExpression;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.ir.expression.UnaryOperation;

import java.util.Map;

@Component
public class ExpressionTranslator {

    private static final Map<BinaryOperation.Operator, String> BINARY_OP_MAP = Map.of(
            BinaryOperation.Operator.ADD, "$add",
            BinaryOperation.Operator.SUBTRACT, "$subtract",
            BinaryOperation.Operator.MULTIPLY, "$multiply",
            BinaryOperation.Operator.DIVIDE, "$divide",
            BinaryOperation.Operator.MOD, "$mod"
    );

    public String translate(Expressionable expr,
                            GenerationContext context) throws CodeGenerationException {
        return switch (expr) {
            case Field field -> translateField(field, context);
            case Constant constant -> translateConstant(constant, context);
            case BinaryOperation binary -> translateBinary(binary, context);
            case UnaryOperation unary -> translateUnary(unary, context);
            case CaseExpression caseExpr -> translateCase(caseExpr, context);
            case Subquery subq -> translateSubquery(subq, context);
            case Aggregate agg -> translateAggregate(agg, context);
            case null, default -> "null";
        };
    }

    private String translateField(Field field, GenerationContext context) {
        String result = context.resolveFieldPath(field.getSource(), field.getField());
        return context.isUseAggregationSyntax() ? "\"$" + result + "\"" : result;
    }

    private String translateConstant(Constant constant, GenerationContext context) {
        return FormatHelper.formatValue(constant.getValue(), context);
    }

    private String translateBinary(BinaryOperation binary,
                                   GenerationContext context) throws CodeGenerationException {
        String left = translate(binary.getLeft(), context);
        String right = translate(binary.getRight(), context);
        String op = BINARY_OP_MAP.get(binary.getOperator());
        if (context.isUseAggregationSyntax()) {
            return "{ " + op + ": [" + left + ", " + right + "] }";
        }
        return "{ " + op + ": [ " + left + ", " + right + " ] }";
    }

    private String translateUnary(UnaryOperation unary,
                                  GenerationContext context) throws CodeGenerationException {
        String operand = translate(unary.getOperand(), context);
        return "{ $multiply: [ -1, " + operand + " ] }";
    }

    private String translateCase(CaseExpression caseExpr,
                                 GenerationContext context) throws CodeGenerationException {
        StringBuilder result = new StringBuilder("{ $switch: {\n");
        context.increaseIndent();
        result.append(context.getIndent()).append("branches: [\n");
        context.increaseIndent();

        var list = caseExpr.getWhenThenList();
        for (int i = 0; i < list.size(); i++) {
            var wt = list.get(i);
            result.append(context.getIndent())
                    .append("{ case: ").append(translate(wt.getCondition(), context))
                    .append(", then: ").append(translate(wt.getResult(), context))
                    .append(" }");
            if (i < list.size() - 1) result.append(",");
            result.append("\n");
        }

        context.decreaseIndent();
        result.append(context.getIndent()).append("],\n");

        if (caseExpr.getElseExpression() != null) {
            result.append(context.getIndent())
                    .append("default: ")
                    .append(translate(caseExpr.getElseExpression(), context))
                    .append("\n");
        } else {
            result.append(context.getIndent()).append("default: null\n");
        }

        context.decreaseIndent();
        result.append(context.getIndent()).append("} }");

        return result.toString();
    }

    private String translateAggregate(Aggregate agg, GenerationContext context) {
        String fieldPath = agg.getField() != null ? agg.getField().getField() : null;
        String type = agg.getType().name().toLowerCase();

        if (agg.isDistinct() && agg.getType() == Aggregate.AggregateType.COUNT) {
            // COUNT(DISTINCT field) - ?????????? ??? ????, ??????? ????? ???????????? ? HAVING
            // ????? $group ??? ???? ????? ?????????? ??? ??, ??? ? ????????
            String alias = agg.getField() != null ? agg.getField().getField() : "count";
            return "\"" + alias + "\"";
        }

        if (fieldPath == null || "*".equals(fieldPath)) {
            if (agg.getType() == Aggregate.AggregateType.COUNT) {
                return "\"count\"";
            }
            return "\"" + type + "\"";
        }

        // ??? ??????? ????????? ?????????? ?????? ?? ???? ????? $group
        return "\"" + fieldPath + "\"";
    }

    private String translateSubquery(Subquery subquery, GenerationContext context) {
        if (subquery instanceof CorrelationSubquery) {
            return "\"$" + context.nextCorrelationName() + "\"";
        }
        return "/* subquery */";
    }
}
