package sql.to.mongodb.translator.translators;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.helpers.FormatHelper;
import sql.to.mongodb.translator.ir.Aggregate;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.expression.BinaryOperation;
import sql.to.mongodb.translator.ir.expression.CaseExpression;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.ir.expression.UnaryOperation;

import java.util.Map;

import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.DOWN;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.NONE;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.UP;
import static sql.to.mongodb.translator.helpers.FormatHelper.indent;

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
            case Aggregate agg -> translateAggregate(agg, context);
            case null, default -> "null";
        };
    }

    private String translateField(Field field, GenerationContext context) {
        if (context.isCorrelationField(field)) {
            String varName = context.getCorrelationVariableForField(field);
            return "\"$$" + varName + "\"";
        }
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
        result.append(indent(UP, context)).append("branches: [\n");

        var list = caseExpr.getWhenThenList();
        for (int i = 0; i < list.size(); i++) {
            var wt = list.get(i);
            result.append(indent(NONE, context))
                    .append("{ case: ").append(translate(wt.getCondition(), context))
                    .append(", then: ").append(translate(wt.getResult(), context))
                    .append(" }");
            if (i < list.size() - 1) result.append(",");
            result.append("\n");
        }

        context.decreaseIndent();
        result.append(indent(NONE, context)).append("],\n");

        if (caseExpr.getElseExpression() != null) {
            result.append(indent(DOWN, context))
                    .append("default: ")
                    .append(translate(caseExpr.getElseExpression(), context))
                    .append("\n");
        } else {
            result.append(indent(DOWN, context)).append("default: null\n");
        }

        result.append(indent(NONE, context)).append("} }");

        return result.toString();
    }

    private String translateAggregate(Aggregate agg, GenerationContext context) {
        String fieldPath = context.resolveFieldPath(agg.getField().getSource(), agg.getField().getField());
        String type = agg.getType().name().toLowerCase();

        if (agg.isDistinct() && agg.getType() == Aggregate.AggregateType.COUNT) {
            String alias = agg.getField() != null ? agg.getField().getField() : "teams";
            return "\"" + alias + "\"";
        }

        if (fieldPath == null || "*".equals(fieldPath)) {
            if (agg.getType() == Aggregate.AggregateType.COUNT) {
                return "\"count\"";
            }
            return "\"" + type + "\"";
        }

        return "\"" + fieldPath + "\"";
    }
}
