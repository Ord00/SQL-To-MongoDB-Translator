package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode.ConditionType.*;

public record ConditionExtractor(Set<String> outerTables, Map<String, String> outerAliases) {

    public ConditionExtractor(Set<String> outerTables, Map<String, String> outerAliases) {
        this.outerTables = outerTables != null ? outerTables : new HashSet<>();
        this.outerAliases = outerAliases != null ? outerAliases : new HashMap<>();
    }

    public ConditionNode extractCondition(Node logicalNode) {
        if (logicalNode == null || logicalNode.getChildren() == null) {
            return null;
        }

        List<ConditionNode> subConditions = new ArrayList<>();
        ConditionNode.ConditionType combineType = null;

        for (Node child : logicalNode.getChildren()) {
            if (child.getNodeType() == NodeType.LOGICAL_CHECK) {
                ConditionNode condition = extractLogicalCheck(child);
                if (condition != null) {
                    subConditions.add(condition);
                }
            } else if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;
                if ("AND".equals(lexeme)) {
                    combineType = AND;
                } else if ("OR".equals(lexeme)) {
                    combineType = OR;
                }
            }
        }

        if (subConditions.isEmpty()) {
            return null;
        }

        if (subConditions.size() == 1) {
            return subConditions.getFirst();
        }

        ConditionNode combined = new ConditionNode();
        combined.setType(combineType != null ? combineType : AND);
        combined.getChildren().addAll(subConditions);
        return combined;
    }

    private ConditionNode extractLogicalCheck(Node logicalCheckNode) {
        if (logicalCheckNode.getChildren() == null) {
            return null;
        }

        ConditionNode condition = new ConditionNode();
        List<String> operands = new ArrayList<>();
        String operator = null;
        boolean notFlag = false;

        for (Node child : logicalCheckNode.getChildren()) {
            switch (child.getNodeType()) {
                case TERMINAL:
                    Token token = child.getToken();
                    TerminalResult result = processTerminal(token, condition, operator, notFlag);
                    operator = result.operator;
                    notFlag = result.notFlag;

                    if (token.category == Category.IDENTIFIER ||
                            token.category == Category.NUMBER) {
                        operands.add(token.lexeme);
                    } else if (token.category == Category.LITERAL) {
                        operands.add("'" + token.lexeme + "'");
                    }
                    break;

                case ARITHMETIC_EXP, IDENTIFIER, AGGREGATE:
                    String expr = ExpressionBuilder.buildExpression(child);
                    operands.add(expr);
                    break;

                case QUERY:
                    condition.setType(determineSubqueryConditionType(operator, notFlag));
                    condition.setValue("SUBQUERY");
                    break;

                case ATTRIBUTES:
                    List<Object> values = extractAttributes(child);
                    condition.setValue(values);
                    condition.setType(IN);
                    break;
            }
        }

        // Устанавливаем поле и значение если не установлены
        if (condition.getField() == null && !operands.isEmpty()) {
            condition.setField(operands.getFirst());
        }
        if (condition.getValue() == null && operands.size() > 1) {
            condition.setValue(operands.get(1));
        }

        // Если тип еще не установлен, устанавливаем по умолчанию
        if (condition.getType() == null && operator != null) {
            condition.setType(COMPARISON);
            condition.setOperator(ExpressionBuilder.convertOperatorToMongo(operator));
        }

        return condition;
    }

    private TerminalResult processTerminal(Token token,
                                           ConditionNode condition,
                                           String currentOperator,
                                           boolean currentNotFlag) {
        String lexeme = token.lexeme;
        String operator = currentOperator;
        boolean notFlag = currentNotFlag;

        switch (token.category) {
            case LOGICAL_OPERATOR:
                operator = lexeme;
                break;

            case KEYWORD:
                if ("NOT".equals(lexeme)) {
                    notFlag = true;
                } else if ("LIKE".equals(lexeme)) {
                    operator = "LIKE";
                    condition.setOperator("$regex");
                } else if ("BETWEEN".equals(lexeme)) {
                    condition.setType(BETWEEN);
                } else if ("IN".equals(lexeme)) {
                    condition.setType(IN);
                } else if ("EXISTS".equals(lexeme)) {
                    condition.setType(notFlag ? NOT_EXISTS : EXISTS);
                } else if ("IS".equals(lexeme)) {
                    operator = "IS";
                }
                break;

            case NULL:
                if ("IS".equals(operator)) {
                    condition.setType(notFlag ? IS_NOT_NULL : IS_NULL);
                }
                break;
        }

        return new TerminalResult(operator, notFlag);
    }

    private ConditionNode.ConditionType determineSubqueryConditionType(String operator,
                                                                       boolean notFlag) {
        if (operator == null) {
            return COMPARISON;
        }

        return switch (operator.toUpperCase()) {
            case "EXISTS" -> notFlag ? NOT_EXISTS : EXISTS;
            case "IN" -> IN;
            default -> COMPARISON;
        };
    }

    private List<Object> extractAttributes(Node attributesNode) {
        List<Object> values = new ArrayList<>();

        if (attributesNode.getChildren() != null) {
            for (Node child : attributesNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    Token token = child.getToken();
                    if (token.category == Category.LITERAL) {
                        values.add("'" + token.lexeme + "'");
                    } else {
                        values.add(token.lexeme);
                    }
                }
            }
        }
        return values;
    }

    // Извлечение условий корреляции из подзапроса
    public List<CorrelationCondition> extractCorrelations(Node subqueryNode) {
        List<CorrelationCondition> correlations = new ArrayList<>();

        if (subqueryNode == null) {
            return correlations;
        }

        CorrelationAnalyzer analyzer = new CorrelationAnalyzer();

        // Передаем информацию о внешних таблицах
        analyzer.analyzeForCorrelations(subqueryNode, outerTables, outerAliases);

        // Ищем WHERE условие
        Node whereCondition = findWhereCondition(subqueryNode);

        if (whereCondition != null) {
            return analyzer.extractCorrelationConditions(whereCondition);
        }

        return analyzer.getCorrelations();
    }

    private Node findWhereCondition(Node queryNode) {
        if (queryNode == null || queryNode.getChildren() == null) {
            return null;
        }

        for (Node child : queryNode.getChildren()) {
            if (child.getNodeType() == NodeType.LOGICAL_CONDITION) {
                return child;
            } else if (child.getNodeType() == NodeType.QUERY) {
                Node where = findWhereCondition(child);
                if (where != null) {
                    return where;
                }
            }
        }
        return null;
    }

    // Вспомогательный класс для возврата нескольких значений
    private static class TerminalResult {
        String operator;
        boolean notFlag;

        TerminalResult(String operator, boolean notFlag) {
            this.operator = operator;
            this.notFlag = notFlag;
        }
    }

    public enum ConditionContext {
        WHERE,
        HAVING,
        JOIN,
        SELECT
    }
}