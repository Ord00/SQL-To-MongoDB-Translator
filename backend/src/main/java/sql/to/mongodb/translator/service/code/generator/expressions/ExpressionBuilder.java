package sql.to.mongodb.translator.service.code.generator.expressions;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.helpers.FormatHelper;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExpressionBuilder {

    private final AggregateExpressionBuilder aggBuilder;

    private static final Map<String, String> OPERATOR_MAPPING = new HashMap<>();
    private static final Map<String, String> LOGICAL_OPERATOR_MAPPING = new HashMap<>();

    static {
        // Арифметические операторы
        OPERATOR_MAPPING.put("+", "$add");
        OPERATOR_MAPPING.put("-", "$subtract");
        OPERATOR_MAPPING.put("*", "$multiply");
        OPERATOR_MAPPING.put("/", "$divide");
        OPERATOR_MAPPING.put("%", "$mod");

        // Логические операторы для агрегации
        LOGICAL_OPERATOR_MAPPING.put("AND", "$and");
        LOGICAL_OPERATOR_MAPPING.put("OR", "$or");
        LOGICAL_OPERATOR_MAPPING.put("NOT", "$not");
        LOGICAL_OPERATOR_MAPPING.put("=", "$eq");
        LOGICAL_OPERATOR_MAPPING.put("!=", "$ne");
        LOGICAL_OPERATOR_MAPPING.put("<>", "$ne");
        LOGICAL_OPERATOR_MAPPING.put("<", "$lt");
        LOGICAL_OPERATOR_MAPPING.put("<=", "$lte");
        LOGICAL_OPERATOR_MAPPING.put(">", "$gt");
        LOGICAL_OPERATOR_MAPPING.put(">=", "$gte");
        LOGICAL_OPERATOR_MAPPING.put("LIKE", "$regexMatch");
        LOGICAL_OPERATOR_MAPPING.put("IN", "$in");
    }

    public ExpressionBuilder(AggregateExpressionBuilder aggBuilder) {
        this.aggBuilder = aggBuilder;
    }

    /**
     * Построение выражения из узла AST
     */
    public String build(Node node, SqlToMongoIR ir, GenerationContext context) {
        if (node == null) return "";

        return switch (node.getNodeType()) {
            case TERMINAL -> buildTerminal(node, context);
            case IDENTIFIER -> buildIdentifier(node, context);
            case ARITHMETIC_EXP -> buildArithmetic(node, ir, context);
            case AGGREGATE -> aggBuilder.build(node, ir, context);
            case CASE -> buildCase(node,  ir, context);
            case LOGICAL_CHECK -> buildLogical(node,  ir, context);
            default -> "";
        };
    }

    /**
     * Построение терминального узла (токена)
     */
    private String buildTerminal(Node node, GenerationContext context) {
        Token token = node.getToken();
        if (token == null) return "";

        if (token.category == Category.LITERAL) {
            return FormatHelper.formatValue(token.lexeme, context);
        } else if (token.category == Category.NULL) {
            return "null";
        } else if (token.category == Category.IDENTIFIER) {
            return token.lexeme;
        } else if (token.category == Category.NUMBER) {
            return token.lexeme;
        } else if (token.category == Category.KEYWORD) {
            if ("TRUE".equalsIgnoreCase(token.lexeme) || "FALSE".equalsIgnoreCase(token.lexeme)) {
                return token.lexeme.toLowerCase();
            }
        }

        return token.lexeme;
    }

    /**
     * Построение идентификатора (поля)
     */
    private String buildIdentifier(Node node, GenerationContext context) {
        if (node.getChildren() == null || node.getChildren().size() < 2) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Node child : node.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                if (!first) {
                    result.append(".");
                }
                result.append(child.getToken().lexeme);
                first = false;
            }
        }

        String identifier = result.toString();

        if (context.isUseAggregationSyntax()) {
            return "\"$" + identifier + "\"";
        }

        return identifier;
    }

    /**
     * Построение арифметического выражения
     */
    private String buildArithmetic(Node node, SqlToMongoIR ir, GenerationContext context) {
        if (node.getChildren() == null || node.getChildren().size() < 3) {
            return "";
        }

        Node left = node.getChildren().get(0);
        Node operator = node.getChildren().get(1);
        Node right = node.getChildren().get(2);

        String leftExpr = build(left, ir, context);
        String rightExpr = build(right, ir, context);
        String op = operator.getToken().lexeme;

        if (context.isUseAggregationSyntax()) {
            String mongoOp = OPERATOR_MAPPING.get(op);
            if (mongoOp != null) {
                return "{ " + mongoOp + ": [ " + leftExpr + ", " + rightExpr + " ] }";
            }
        }

        return leftExpr + " " + op + " " + rightExpr;
    }

    /**
     * Построение CASE выражения
     */
    private String buildCase(Node node, SqlToMongoIR ir, GenerationContext context) {
        if (node.getChildren() == null) return "";

        StringBuilder result = new StringBuilder();
        boolean inWhen = false;
        boolean inThen = false;
        boolean hasElse = false;
        int branchCount = 0;
        String caseValue = null;

        if (context.isUseAggregationSyntax()) {
            result.append("{ $switch: {\n");
            context.increaseIndent();
            result.append(context.getIndent()).append("branches: [\n");
            context.increaseIndent();
        }

        for (Node child : node.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;

                switch (lexeme) {
                    case "CASE":
                        // Начало CASE - может быть значение для сравнения
                        break;

                    case "WHEN":
                        if (context.isUseAggregationSyntax()) {
                            if (branchCount > 0) {
                                result.append(",\n");
                            }
                            result.append(context.getIndent()).append("{ case: ");
                            branchCount++;
                        }
                        inWhen = true;
                        inThen = false;
                        break;

                    case "THEN":
                        if (context.isUseAggregationSyntax()) {
                            result.append(", then: ");
                        }
                        inWhen = false;
                        inThen = true;
                        break;

                    case "ELSE":
                        if (context.isUseAggregationSyntax()) {
                            result.append("\n").append(context.getIndent())
                                    .append("],\n")
                                    .append(context.getIndent())
                                    .append("default: ");
                        }
                        hasElse = true;
                        inWhen = false;
                        inThen = true;
                        break;

                    case "END":
                        // Завершение
                        break;

                    default:
                        if (!context.isUseAggregationSyntax()) {
                            result.append(lexeme).append(" ");
                        }
                        break;
                }
            } else {
                String expr = build(child, ir, context);
                if (inWhen) {
                    // Если есть caseValue, используем его для сравнения
                    if (caseValue != null && context.isUseAggregationSyntax()) {
                        result.append("{ $eq: [ ").append(caseValue).append(", ").append(expr).append(" ] }");
                    } else {
                        result.append(expr);
                    }
                } else if (inThen) {
                    result.append(expr);
                } else if ("CASE".equals(caseValue) && context.isUseAggregationSyntax()) {
                    // Это значение для сравнения во всем CASE
                    caseValue = expr;
                } else if (!context.isUseAggregationSyntax()) {
                    result.append(expr).append(" ");
                }
            }
        }

        if (context.isUseAggregationSyntax()) {
            if (!hasElse) {
                result.append("\n").append(context.getIndent())
                        .append("],\n")
                        .append(context.getIndent())
                        .append("default: null");
            }
            context.decreaseIndent();
            result.append("\n").append(context.getIndent()).append("]\n");
            context.decreaseIndent();
            result.append(context.getIndent()).append("} }");
        } else {
            String caseStr = result.toString().trim();
            if (caseStr.endsWith("END")) {
                return caseStr;
            }
            return "CASE " + caseStr + " END";
        }

        return result.toString();
    }

    /**
     * Построение логического выражения
     */
    private String buildLogical(Node node, SqlToMongoIR ir, GenerationContext context) {
        if (context.isUseAggregationSyntax()) {
            return buildLogicalForAggregation(node, ir, context);
        } else {
            return buildLogicalForFind(node, ir, context);
        }
    }

    /**
     * Построение логического выражения для aggregation pipeline
     */
    private String buildLogicalForAggregation(Node node, SqlToMongoIR ir, GenerationContext context) {
        if (node == null || node.getChildren() == null) return "{}";

        // Определяем тип логической операции
        String operator = null;
        List<String> operands = new ArrayList<>();
        boolean isNegated = false;

        for (Node child : node.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                String lexeme = token.lexeme;

                if (token.category == Category.LOGICAL_OPERATOR) {
                    operator = lexeme;
                } else if ("NOT".equals(lexeme)) {
                    isNegated = true;
                } else if ("AND".equals(lexeme) || "OR".equals(lexeme)) {
                    operator = lexeme;
                } else if (token.category == Category.IDENTIFIER ||
                        token.category == Category.NUMBER ||
                        token.category == Category.LITERAL) {
                    // Это операнд
                    operands.add(FormatHelper.formatValue(lexeme, context));
                }
            } else {
                // Рекурсивно строим вложенное выражение
                String expr = build(child,  ir, context);
                if (!expr.isEmpty()) {
                    operands.add(expr);
                }
            }
        }

        // Если нет оператора, пытаемся определить по контексту
        if (operator == null && operands.size() >= 2) {
            // По умолчанию считаем AND
            operator = "AND";
        }

        // Строим выражение

        return buildLogicalExpression(operator, operands, isNegated);
    }

    /**
     * Построение логического выражения для find() запроса
     */
    private String buildLogicalForFind(Node node, SqlToMongoIR ir, GenerationContext context) {
        if (node == null || node.getChildren() == null) return "{}";

        StringBuilder result = new StringBuilder();
        String currentOperator = null;
        List<String> conditions = new ArrayList<>();

        for (Node child : node.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                String lexeme = token.lexeme;

                if ("AND".equals(lexeme) || "OR".equals(lexeme)) {
                    currentOperator = lexeme;
                } else if (token.category == Category.LOGICAL_OPERATOR) {
                    // Это оператор сравнения, обработаем позже
                    continue;
                }
            } else if (child.getNodeType() == NodeType.IDENTIFIER) {
                // Это поле
                String field = buildIdentifier(child, context);
                // Ищем следующий токен для оператора и значения
                String operator = "=";
                String value = "null";

                // Пытаемся найти оператор и значение в следующих узлах
                int index = node.getChildren().indexOf(child);
                if (index + 1 < node.getChildren().size()) {
                    Node next = node.getChildren().get(index + 1);
                    if (next.getNodeType() == NodeType.TERMINAL &&
                            next.getToken().category == Category.LOGICAL_OPERATOR) {
                        operator = next.getToken().lexeme;
                    }
                }
                if (index + 2 < node.getChildren().size()) {
                    Node nextNext = node.getChildren().get(index + 2);
                    value = build(nextNext, ir, context);
                }

                String condition = buildComparisonForFind(field, operator, value);
                if (!condition.isEmpty()) {
                    conditions.add(condition);
                }
            }
        }

        // Объединяем условия
        if (conditions.isEmpty()) {
            return "{}";
        } else if (conditions.size() == 1) {
            return conditions.getFirst();
        } else {
            result.append("{ ");
            if ("OR".equals(currentOperator)) {
                result.append("$or: [ ");
            } else {
                result.append("$and: [ ");
            }
            result.append(String.join(", ", conditions));
            result.append(" ] }");
            return result.toString();
        }
    }

    /**
     * Построение логического выражения для aggregation
     */
    private String buildLogicalExpression(String operator, List<String> operands, boolean isNegated) {
        if (operands.isEmpty()) return "{}";
        if (operands.size() == 1 && !isNegated) return operands.getFirst();

        StringBuilder expr = new StringBuilder();

        // Маппинг операторов
        String mongoOp = LOGICAL_OPERATOR_MAPPING.get(operator);
        if (mongoOp == null) {
            mongoOp = "$and"; // По умолчанию
        }

        // Для NOT оператора
        if (isNegated) {
            if (operands.size() == 1) {
                expr.append("{ $not: [ ").append(operands.getFirst()).append(" ] }");
            } else {
                expr.append("{ $nor: [ ");
                for (int i = 0; i < operands.size(); i++) {
                    expr.append(operands.get(i));
                    if (i < operands.size() - 1) {
                        expr.append(", ");
                    }
                }
                expr.append(" ] }");
            }
            return expr.toString();
        }

        // Для AND/OR
        if (operands.size() > 1 || "$not".equals(mongoOp)) {
            expr.append("{ ").append(mongoOp).append(": [ ");
            for (int i = 0; i < operands.size(); i++) {
                expr.append(operands.get(i));
                if (i < operands.size() - 1) {
                    expr.append(", ");
                }
            }
            expr.append(" ] }");
        } else {
            expr.append(operands.getFirst());
        }

        return expr.toString();
    }

    /**
     * Построение условия сравнения для find()
     */
    private String buildComparisonForFind(String field, String operator, String value) {
        String mongoOp = LOGICAL_OPERATOR_MAPPING.get(operator);
        if (mongoOp == null) {
            mongoOp = "$eq";
        }

        // Для простого равенства используем сокращенную форму
        if ("$eq".equals(mongoOp)) {
            return "{ \"" + field + "\": " + value + " }";
        } else {
            return "{ \"" + field + "\": { " + mongoOp + ": " + value + " } }";
        }
    }

    /**
     * Построение условия LIKE для find()
     */
    private String buildLikeCondition(String field, String pattern) {
        // Убираем кавычки из паттерна
        String cleanPattern = pattern.replace("'", "").replace("\"", "");

        // Преобразуем SQL LIKE в regex
        String regex = cleanPattern
                .replace("%", ".*")
                .replace("_", ".");

        return "{ \"" + field + "\": { $regex: \"" + regex + "\" } }";
    }

    /**
     * Построение условия IN для find()
     */
    private String buildInCondition(String field, List<String> values) {
        return "{ \"" + field + "\": { $in: [ " + String.join(", ", values) + " ] } }";
    }

    /**
     * Построение условия BETWEEN для find()
     */
    private String buildBetweenCondition(String field, String from, String to) {
        return "{ \"" + field + "\": { $gte: " + from + ", $lte: " + to + " } }";
    }

    /**
     * Построение условия EXISTS для find()
     */
    private String buildExistsCondition(String field, boolean exists) {
        return "{ \"" + field + "\": { $exists: " + exists + " } }";
    }

    private int countCommas(String str) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ',') count++;
        }
        return count;
    }

    private String extractLogicalSummary(Node node) {
        if (node == null) return "";

        StringBuilder summary = new StringBuilder();
        if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    summary.append(child.getToken().lexeme).append(" ");
                } else {
                    summary.append("... ");
                }
            }
        }
        return summary.toString().trim();
    }
}
