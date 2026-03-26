package sql.to.mongodb.translator.service.intermediate.representation.processors;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.model.Constant;
import sql.to.mongodb.translator.service.intermediate.representation.model.Field;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.*;
import sql.to.mongodb.translator.service.intermediate.representation.model.projection.AggregateProjection;
import sql.to.mongodb.translator.service.intermediate.representation.model.projection.ProjectionField;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.*;

public class ExpressionBuilder {

    private static final Map<String, AggregateProjection.AggregateType> AGGREGATE_MAPPING = new HashMap<>();
    private static final Map<String, BinaryOperation.Operator> OPERATOR_MAPPING = new HashMap<>();

    static {
        AGGREGATE_MAPPING.put("COUNT", AggregateProjection.AggregateType.COUNT);
        AGGREGATE_MAPPING.put("SUM", AggregateProjection.AggregateType.SUM);
        AGGREGATE_MAPPING.put("AVG", AggregateProjection.AggregateType.AVG);
        AGGREGATE_MAPPING.put("MIN", AggregateProjection.AggregateType.MIN);
        AGGREGATE_MAPPING.put("MAX", AggregateProjection.AggregateType.MAX);

        OPERATOR_MAPPING.put("+", BinaryOperation.Operator.ADD);
        OPERATOR_MAPPING.put("-", BinaryOperation.Operator.SUBTRACT);
        OPERATOR_MAPPING.put("*", BinaryOperation.Operator.MULTIPLY);
        OPERATOR_MAPPING.put("/", BinaryOperation.Operator.DIVIDE);
        OPERATOR_MAPPING.put("%", BinaryOperation.Operator.MOD);
    }

    /**
     * Построение AST арифметического выражения
     */
    public static Arithmetical buildArithmeticExpression(Node arithNode) {
        if (arithNode == null || arithNode.getChildren() == null) {
            return null;
        }

        List<Object> tokens = extractTokens(arithNode);
        ArithmeticParser parser = new ArithmeticParser(tokens);
        return parser.parse();
    }

    /**
     * Построение Field из узла IDENTIFIER
     */
    public static Field buildField(Node identifierNode) {
        Field field = new Field();
        if (identifierNode.getChildren() == null || identifierNode.getChildren().isEmpty()) {
            return field;
        }

        List<String> parts = new ArrayList<>();
        for (Node child : identifierNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                parts.add(child.getToken().lexeme);
            }
        }

        if (parts.size() == 1) {
            field.setField(parts.getFirst());
        } else if (parts.size() >= 2) {
            field.setSource(parts.get(0));
            field.setField(parts.get(1));
        }

        return field;
    }

    /**
     * Построение ProjectionField из узла IDENTIFIER
     */
    public static ProjectionField buildFieldProjection(Node identifierNode) {
        ProjectionField field = new ProjectionField();
        if (identifierNode.getChildren() == null || identifierNode.getChildren().isEmpty()) {
            return field;
        }

        List<String> parts = new ArrayList<>();
        for (Node child : identifierNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                parts.add(child.getToken().lexeme);
            }
        }

        if (parts.size() == 1) {
            field.setField(parts.getFirst());
        } else if (parts.size() >= 2) {
            field.setSource(parts.get(0));
            field.setField(parts.get(1));
        }

        field.setAlias(extractAlias(identifierNode));
        return field;
    }

    /**
     * Построение AggregateProjection из узла AGGREGATE
     */
    public static AggregateProjection buildAggregateFunction(Node aggregateNode) {
        if (aggregateNode == null) {
            return null;
        }

        AggregateProjection aggregate = new AggregateProjection();
        String functionName = null;
        String fieldName = null;
        boolean distinct = false;

        for (Node child : aggregateNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                if (token.category == Category.AGGREGATE ||
                        (token.category == Category.KEYWORD &&
                                AGGREGATE_MAPPING.containsKey(token.lexeme.toUpperCase()))) {
                    functionName = token.lexeme.toUpperCase();
                } else if ("DISTINCT".equals(token.lexeme)) {
                    distinct = true;
                } else if ("*".equals(token.lexeme)) {
                    fieldName = "*";
                }
            } else if (child.getNodeType() == NodeType.IDENTIFIER) {
                fieldName = buildIdentifierString(child);
            }
        }

        if (functionName != null) {
            aggregate.setType(AGGREGATE_MAPPING.get(functionName));
            ProjectionField field = new ProjectionField();
            if (fieldName != null && fieldName.contains(".")) {
                String[] parts = fieldName.split("\\.");
                field.setSource(parts[0]);
                field.setField(parts[1]);
            } else {
                field.setField(fieldName);
            }
            aggregate.setField(field);
            aggregate.setDistinct(distinct);
        }

        aggregate.setAlias(extractAlias(aggregateNode));
        return aggregate;
    }

    /**
     * Построение строки идентификатора
     */
    public static String buildIdentifierString(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (Node child : identifierNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                parts.add(child.getToken().lexeme);
            }
        }

        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return parts.get(0) + "." + parts.get(1);
    }

    /**
     * Построение строки выражения (для обратной совместимости)
     */
    public static String buildExpressionString(Node node) {
        if (node == null) return "";
        return switch (node.getNodeType()) {
            case TERMINAL -> buildTerminalString(node);
            case IDENTIFIER -> buildIdentifierString(node);
            case ARITHMETIC_EXP -> buildArithmeticExpressionString(node);
            case AGGREGATE -> buildAggregateString(node);
            case CASE -> buildCaseExpressionString(node);
            case LOGICAL_CHECK -> buildLogicalCheckString(node);
            default -> "";
        };
    }

    private static String buildTerminalString(Node terminalNode) {
        Token token = terminalNode.getToken();
        return switch (token.category) {
            case LITERAL -> Constant.ofString(token.lexeme).toString();
            case NUMBER -> Constant.ofNumber(token.lexeme).toString();
            case NULL -> Constant.ofNull().toString();
            default -> token.lexeme;
        };
    }

    private static String buildArithmeticExpressionString(Node arithNode) {
        return getString(arithNode);
    }

    private static String getString(Node arithNode) {
        StringBuilder sb = new StringBuilder();
        if (arithNode.getChildren() != null) {
            for (Node child : arithNode.getChildren()) {
                String part = buildExpressionString(child);
                if (!part.isEmpty()) {
                    sb.append(part).append(" ");
                }
            }
        }
        return sb.toString().trim();
    }

    private static String buildAggregateString(Node aggregateNode) {
        StringBuilder sb = new StringBuilder();
        if (aggregateNode.getChildren() != null) {
            for (Node child : aggregateNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    sb.append(child.getToken().lexeme);
                } else {
                    sb.append(buildExpressionString(child));
                }
            }
        }
        return sb.toString();
    }

    private static String buildCaseExpressionString(Node caseNode) {
        StringBuilder sb = new StringBuilder("CASE ");
        boolean hasWhen = false;
        if (caseNode.getChildren() != null) {
            for (Node child : caseNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    String lexeme = child.getToken().lexeme;
                    switch (lexeme) {
                        case "WHEN" -> { sb.append("WHEN "); hasWhen = true; }
                        case "THEN" -> sb.append("THEN ");
                        case "ELSE" -> sb.append("ELSE ");
                        case "END" -> sb.append("END");
                        default -> sb.append(lexeme).append(" ");
                    }
                } else {
                    String expr = buildExpressionString(child);
                    if (!expr.isEmpty()) {
                        if (hasWhen) {
                            sb.append(expr);
                            hasWhen = false;
                        } else {
                            sb.append(expr).append(" ");
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static String buildLogicalCheckString(Node logicalCheckNode) {
        return getString(logicalCheckNode);
    }

    private static List<Object> extractTokens(Node arithNode) {
        List<Object> tokens = new ArrayList<>();
        extractTokensRecursive(arithNode, tokens);
        return tokens;
    }

    private static void extractTokensRecursive(Node node, List<Object> tokens) {
        if (node == null) return;

        if (node.getNodeType() == NodeType.TERMINAL) {
            Token token = node.getToken();
            if (token.category == Category.ARITHMETIC_OPERATOR) {
                tokens.add(token.lexeme);
            } else if (token.category == Category.NUMBER) {
                tokens.add(Constant.ofNumber(token.lexeme));
            } else if (token.category == Category.IDENTIFIER) {
                tokens.add(token.lexeme);
            } else if ("(".equals(token.lexeme) || ")".equals(token.lexeme)) {
                tokens.add(token.lexeme);
            }
        } else if (node.getNodeType() == NodeType.IDENTIFIER) {
            tokens.add(buildIdentifierString(node));
        } else if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                extractTokensRecursive(child, tokens);
            }
        }
    }

    private static String extractAlias(Node node) {
        return getString(node);
    }

    /**
     * Внутренний парсер арифметических выражений
     * Возвращает готовые объекты Field, Constant, BinaryOperation, UnaryOperation
     */
    private static class ArithmeticParser {
        private final List<Object> tokens;
        private int pos;

        ArithmeticParser(List<Object> tokens) {
            this.tokens = tokens;
            this.pos = 0;
        }

        Arithmetical parse() {
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
                return constant;  // Constant implements Expressionable
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
                    return field;  // Field implements Expressionable
                }
            }

            return null;
        }
    }
}