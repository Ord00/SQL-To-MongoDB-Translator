package sql.to.mongodb.translator.processors;

import sql.to.mongodb.translator.IRGenerator;
import sql.to.mongodb.translator.exceptions.IRGenerationException;
import sql.to.mongodb.translator.ir.Aggregate;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.expression.Arithmetical;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.Projectionable;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Category;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpressionBuilder {

    private static final Map<String, AggregateProjection.AggregateType> AGGREGATE_MAPPING = new HashMap<>();

    static {
        AGGREGATE_MAPPING.put("COUNT", AggregateProjection.AggregateType.COUNT);
        AGGREGATE_MAPPING.put("SUM", AggregateProjection.AggregateType.SUM);
        AGGREGATE_MAPPING.put("AVG", AggregateProjection.AggregateType.AVG);
        AGGREGATE_MAPPING.put("MIN", AggregateProjection.AggregateType.MIN);
        AGGREGATE_MAPPING.put("MAX", AggregateProjection.AggregateType.MAX);
    }

    public static Field buildField(Node identifierNode) {
        Field field = new Field();
        extractFieldParts(identifierNode, field);

        return field;
    }

    public static boolean extractFieldParts(Node identifierNode, Field field) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().isEmpty()) {
            return true;
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
        return false;
    }

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
                if (token.category == Category.AGGREGATE
                        || (token.category == Category.KEYWORD
                        && AGGREGATE_MAPPING.containsKey(token.lexeme.toUpperCase()))) {
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

    public static int processAlias(List<Node> columns, int i, Projectionable field) {
        int result = i;
        String alias = extractAlias(columns, result);
        if (alias != null) {
            result += 2;
            field.setAlias(alias);
        } else {
            result += 1;
        }
        return result;
    }

    public static String extractAlias(List<Node> columns, int i) {

        Token curToken = columns.get(i).getToken();
        if (i < columns.size() - 1 && curToken != null && "AS".equals(curToken.lexeme)) {
            return columns.get(i + 1).getToken().lexeme;
        }
        return null;
    }

    public static String extractAlias(Node node) {

        List<Node> children = node.getChildren();
        int size = children.size();

        if ("AS".equals(children.get(size - 2).getToken().lexeme)) {
            return children.get(size - 1).getToken().lexeme;
        }
        return null;
    }

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
                        case "WHEN" -> {
                            sb.append("WHEN ");
                            hasWhen = true;
                        }
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

    public static Arithmetical buildArithmeticExpression(Node arithNode) {
        if (arithNode == null || arithNode.getChildren() == null) {
            return null;
        }

        List<Object> tokens = extractTokens(arithNode);
        ArithmeticParser parser = new ArithmeticParser(tokens);
        return parser.parse();
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
            if (token.category == Category.ARITHMETIC_OPERATOR || token.category == Category.ALL) {
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

    public static Expressionable buildAggregateExpression(Node aggregateNode,
                                                          IRGenerator.GenerationContext ctx) {
        Aggregate aggregate = new Aggregate();
        String functionName = null;
        String fieldName = null;
        boolean distinct = false;

        for (Node child : aggregateNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                String lexeme = token.lexeme.toUpperCase();
                if (lexeme.equals("COUNT") || lexeme.equals("SUM") ||
                        lexeme.equals("AVG") || lexeme.equals("MIN") ||
                        lexeme.equals("MAX")) {
                    functionName = lexeme;
                } else if ("DISTINCT".equals(token.lexeme)) {
                    distinct = true;
                } else if ("*".equals(token.lexeme)) {
                    fieldName = "*";
                }
            } else if (child.getNodeType() == NodeType.IDENTIFIER) {
                fieldName = ExpressionBuilder.buildIdentifierString(child);
            }
        }

        if (functionName != null) {
            try {
                Aggregate.AggregateType type = Aggregate.AggregateType.valueOf(functionName);
                aggregate.setType(type);

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

                // Отмечаем, что в запросе есть агрегатные функции
                if (ctx.ir != null) {
                    ctx.ir.setHasAggregateFunctions(true);
                }

                return aggregate;
            } catch (IllegalArgumentException e) {
                throw  new IRGenerationException(e.getMessage());
            }
        }

        return null;
    }
}