package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * ????????? ????????? ??? ?????????????? ?????????????
 */
public class ExpressionBuilder {

    private static final Map<String, String> AGGREGATE_MAPPING = new HashMap<>();
    private static final Map<String, String> OPERATOR_MAPPING = new HashMap<>();

    static {
        // ??????? ?????????? ??????? SQL -> MongoDB
        AGGREGATE_MAPPING.put("COUNT", "$sum");
        AGGREGATE_MAPPING.put("SUM", "$sum");
        AGGREGATE_MAPPING.put("AVG", "$avg");
        AGGREGATE_MAPPING.put("MIN", "$min");
        AGGREGATE_MAPPING.put("MAX", "$max");

        // ??????? ?????????? SQL -> MongoDB
        OPERATOR_MAPPING.put("=", "$eq");
        OPERATOR_MAPPING.put("!=", "$ne");
        OPERATOR_MAPPING.put("<>", "$ne");
        OPERATOR_MAPPING.put("<", "$lt");
        OPERATOR_MAPPING.put("<=", "$lte");
        OPERATOR_MAPPING.put(">", "$gt");
        OPERATOR_MAPPING.put(">=", "$gte");
        OPERATOR_MAPPING.put("LIKE", "$regex");
        OPERATOR_MAPPING.put("AND", "$and");
        OPERATOR_MAPPING.put("OR", "$or");
        OPERATOR_MAPPING.put("NOT", "$not");
        OPERATOR_MAPPING.put("IN", "$in");
        OPERATOR_MAPPING.put("NOT IN", "$nin");
        OPERATOR_MAPPING.put("BETWEEN", "$and"); // ??????????? ?????????
    }

    /**
     * ?????????? ?????? ????????? ?? AST ????
     */
    public static String buildExpression(Node node) {
        if (node == null) return "";

        switch (node.getNodeType()) {
            case TERMINAL:
                return buildTerminal(node);
            case IDENTIFIER:
                return buildIdentifier(node);
            case ARITHMETIC_EXP:
                return buildArithmeticExpression(node);
            case AGGREGATE:
                return buildAggregateFunction(node);
            case CASE:
                return buildCaseExpression(node);
            case LOGICAL_CHECK:
                return buildLogicalCheck(node);
            default:
                return "";
        }
    }

    private static String buildTerminal(Node terminalNode) {
        Token token = terminalNode.getToken();
        if (token == null) return "";

        // ????????? ??????????? ???????
        if (token.category == Category.LITERAL) {
            return "'" + token.lexeme + "'";
        } else if (token.category == Category.NULL) {
            return "null";
        }

        return token.lexeme;
    }

    private static String buildIdentifier(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2) {
            return "";
        }

        String tablePart = "";
        String columnPart = "";

        for (Node child : identifierNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                if (tablePart.isEmpty()) {
                    tablePart = child.getToken().lexeme;
                } else {
                    columnPart = child.getToken().lexeme;
                }
            }
        }

        if (!tablePart.isEmpty() && !columnPart.isEmpty()) {
            return tablePart + "." + columnPart;
        } else if (!columnPart.isEmpty()) {
            return columnPart;
        }

        return "";
    }

    private static String buildArithmeticExpression(Node arithNode) {
        StringBuilder sb = new StringBuilder();

        if (arithNode.getChildren() != null) {
            for (Node child : arithNode.getChildren()) {
                String part = buildExpression(child);
                if (!part.isEmpty()) {
                    sb.append(part).append(" ");
                }
            }
        }

        return sb.toString().trim();
    }

    private static String buildAggregateFunction(Node aggregateNode) {
        StringBuilder sb = new StringBuilder();

        if (aggregateNode.getChildren() != null) {
            for (Node child : aggregateNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    String funcName = child.getToken().lexeme;
                    String mongoFunc = AGGREGATE_MAPPING.getOrDefault(funcName.toUpperCase(), funcName);
                    sb.append(mongoFunc);
                } else {
                    String arg = buildExpression(child);
                    sb.append("(").append(arg).append(")");
                }
            }
        }

        return sb.toString();
    }

    private static String buildCaseExpression(Node caseNode) {
        StringBuilder sb = new StringBuilder("CASE ");
        boolean hasWhen = false;

        if (caseNode.getChildren() != null) {
            for (Node child : caseNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    String lexeme = child.getToken().lexeme;
                    if ("WHEN".equals(lexeme)) {
                        sb.append("WHEN ");
                        hasWhen = true;
                    } else if ("THEN".equals(lexeme)) {
                        sb.append("THEN ");
                    } else if ("ELSE".equals(lexeme)) {
                        sb.append("ELSE ");
                    } else if ("END".equals(lexeme)) {
                        sb.append("END");
                    } else {
                        sb.append(lexeme).append(" ");
                    }
                } else {
                    String expr = buildExpression(child);
                    if (!expr.isEmpty()) {
                        if (hasWhen) {
                            // ??? ??????? WHEN
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

    private static String buildLogicalCheck(Node logicalCheckNode) {
        StringBuilder sb = new StringBuilder();

        if (logicalCheckNode.getChildren() != null) {
            String operator = null;
            boolean inExpression = false;

            for (Node child : logicalCheckNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    Token token = child.getToken();
                    String lexeme = token.lexeme;

                    if (token.category == Category.LOGICAL_OPERATOR) {
                        operator = OPERATOR_MAPPING.getOrDefault(lexeme, lexeme);
                        sb.append(operator).append(" ");
                    } else if ("IN".equals(lexeme)) {
                        operator = "$in";
                        sb.append("IN ");
                    } else if ("BETWEEN".equals(lexeme)) {
                        operator = "$and";
                        sb.append("BETWEEN ");
                    } else if ("LIKE".equals(lexeme)) {
                        operator = "$regex";
                        sb.append("LIKE ");
                    } else if ("EXISTS".equals(lexeme)) {
                        sb.append("EXISTS ");
                    } else if ("NOT".equals(lexeme)) {
                        sb.append("NOT ");
                    } else if ("AND".equals(lexeme) || "OR".equals(lexeme)) {
                        sb.append(lexeme).append(" ");
                    }
                } else {
                    String expr = buildExpression(child);
                    if (!expr.isEmpty()) {
                        if ("IN".equals(operator) || "BETWEEN".equals(operator)) {
                            if (!inExpression) {
                                sb.append("(");
                                inExpression = true;
                            }
                            sb.append(expr).append(", ");
                        } else {
                            sb.append(expr).append(" ");
                        }
                    }
                }
            }

            if (inExpression) {
                // ??????? ????????? ??????? ? ??????
                if (sb.length() > 2 && sb.charAt(sb.length() - 2) == ',') {
                    sb.delete(sb.length() - 2, sb.length());
                }
                sb.append(")");
            }
        }

        return sb.toString().trim();
    }

    /**
     * ??????????? SQL ????????? ? MongoDB ????????
     */
    public static String convertOperatorToMongo(String sqlOperator) {
        return OPERATOR_MAPPING.getOrDefault(sqlOperator, sqlOperator);
    }

    /**
     * ??????????? SQL ?????????? ??????? ? MongoDB
     */
    public static String convertAggregateToMongo(String sqlAggregate) {
        return AGGREGATE_MAPPING.getOrDefault(sqlAggregate.toUpperCase(), sqlAggregate);
    }
}
