package sql.to.mongodb.translator.service.intermediate.representation.processors;

import sql.to.mongodb.translator.service.intermediate.representation.model.Constant;
import sql.to.mongodb.translator.service.intermediate.representation.model.Field;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Arithmetical;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.BinaryOperation;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.UnaryOperation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Внутренний парсер арифметических выражений
 * Возвращает готовые объекты Field, Constant, BinaryOperation, UnaryOperation
 */
public class ArithmeticParser {
    private final List<Object> tokens;
    private int pos;

    private static final Map<String, BinaryOperation.Operator> OPERATOR_MAPPING = new HashMap<>();

    ArithmeticParser(List<Object> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    static {
        OPERATOR_MAPPING.put("+", BinaryOperation.Operator.ADD);
        OPERATOR_MAPPING.put("-", BinaryOperation.Operator.SUBTRACT);
        OPERATOR_MAPPING.put("*", BinaryOperation.Operator.MULTIPLY);
        OPERATOR_MAPPING.put("/", BinaryOperation.Operator.DIVIDE);
        OPERATOR_MAPPING.put("%", BinaryOperation.Operator.MOD);
    }

    public Arithmetical parse() {
        return parseExpression();
    }

    private Arithmetical parseExpression() {
        Arithmetical left = parseTerm();

        while (pos < tokens.size()) {
            Object token = tokens.get(pos);
            if (!(token instanceof String op)) break;

            if (op.equals("+") || op.equals("-")) {
                pos++;
                Arithmetical right = parseTerm();
                BinaryOperation.Operator operator = OPERATOR_MAPPING.get(op);
                if (operator != null) {
                    left = new BinaryOperation(left, right, operator);
                }
            } else {
                break;
            }
        }
        return left;
    }

    private Arithmetical parseTerm() {
        Arithmetical left = parseFactor();

        while (pos < tokens.size()) {
            Object token = tokens.get(pos);
            if (!(token instanceof String op)) break;

            if (op.equals("*") || op.equals("/") || op.equals("%")) {
                pos++;
                Arithmetical right = parseFactor();
                BinaryOperation.Operator operator = OPERATOR_MAPPING.get(op);
                if (operator != null) {
                    left = new BinaryOperation(left, right, operator);
                }
            } else {
                break;
            }
        }
        return left;
    }

    private Arithmetical parseFactor() {
        if (pos >= tokens.size()) return null;

        Object token = tokens.get(pos);

        if (token instanceof Constant constant) {
            pos++;
            return constant;
        } else if (token instanceof String str) {
            if (str.equals("(")) {
                pos++;
                Arithmetical expr = parseExpression();
                if (pos < tokens.size() && tokens.get(pos).equals(")")) {
                    pos++;
                }
                return expr;
            } else if (str.equals("-")) {
                pos++;
                Arithmetical operand = parseFactor();
                return new UnaryOperation(operand, UnaryOperation.UnaryOperator.NEGATE);
            } else {
                // Идентификатор поля
                pos++;
                Field field = new Field();
                if (str.contains(".")) {
                    String[] parts = str.split("\\.");
                    field.setSource(parts[0]);
                    field.setField(parts[1]);
                } else {
                    field.setField(str);
                }
                return field;
            }
        }

        return null;
    }
}