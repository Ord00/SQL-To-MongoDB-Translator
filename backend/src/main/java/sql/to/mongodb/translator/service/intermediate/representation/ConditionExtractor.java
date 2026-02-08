package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode.ConditionType.COMPARISON;
import static sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode.ConditionType.EXISTS;
import static sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode.ConditionType.IN;
import static sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode.ConditionType.NOT_EXISTS;

// Извлекатель условий из AST
public class ConditionExtractor {

    public static ConditionNode extractCondition(Node logicalNode,
                                                 ConditionContext context) {

        if (logicalNode == null || logicalNode.getChildren() == null) {
            return null;
        }

        List<ConditionNode> subConditions = new ArrayList<>();
        ConditionNode.ConditionType combineType = null;

        for (Node child : logicalNode.getChildren()) {

            if (child.getNodeType() == NodeType.LOGICAL_CHECK) {

                ConditionNode condition = extractLogicalCheck(child, context);
                if (condition != null) {
                    subConditions.add(condition);
                }
            } else if (child.getNodeType() == NodeType.TERMINAL) {

                String lexeme = child.getToken().lexeme;
                if ("AND".equals(lexeme)) {
                    combineType = ConditionNode.ConditionType.AND;
                } else if ("OR".equals(lexeme)) {
                    combineType = ConditionNode.ConditionType.OR;
                }
            }
        }

        if (subConditions.isEmpty()) {
            return null;
        }

        if (subConditions.size() == 1) {
            return subConditions.getFirst();
        }

        // Создаем комбинированное условие
        ConditionNode combined = new ConditionNode();
        combined.setType(combineType != null ? combineType : ConditionNode.ConditionType.AND);
        combined.getChildren().addAll(subConditions);

        return combined;
    }


    private static ConditionNode extractLogicalCheck(Node logicalCheckNode,
                                                     ConditionContext context) {

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
                    processTerminalInCondition(token, condition, operands, operator, notFlag);
                    break;

                case ARITHMETIC_EXP:
                    String expr = ExpressionBuilder.buildExpression(child);
                    condition.setField(expr);
                    break;

                case AGGREGATE:
                    String aggExpr = ExpressionBuilder.buildExpression(child);
                    condition.setField(aggExpr);
                    if (context == ConditionContext.HAVING) {
                        condition.setType(COMPARISON);
                    }
                    break;

                case IDENTIFIER:
                    String identifier = ExpressionBuilder.buildExpression(child);
                    operands.add(identifier);
                    break;

                case QUERY:
                    // Подзапрос в условии
                    condition.setValue("SUBQUERY");
                    setSubqueryConditionType(condition, operator, notFlag);
                    break;

                case ATTRIBUTES:
                    List<Object> values = extractAttributes(child);
                    condition.setValue(values);
                    condition.setType(IN);
                    break;
            }
        }

        // Если тип еще не установлен, устанавливаем по умолчанию
        if (condition.getType() == null) {

            if (operator != null) {
                condition.setType(COMPARISON);
                condition.setOperator(operator);
            }

            // Устанавливаем поле и значение из операндов
            if (operands.size() >= 2 && condition.getField() == null) {
                condition.setField(operands.get(0));
                condition.setValue(operands.get(1));
            }
        }

        return condition;
    }

    private static void processTerminalInCondition(Token token,
                                                   ConditionNode condition,
                                                   List<String> operands,
                                                   String operator,
                                                   boolean notFlag) {
        String lexeme = token.lexeme;

        switch (token.category) {
            case LOGICAL_OPERATOR:
                operator = lexeme;
                condition.setOperator(ExpressionBuilder.convertOperatorToMongo(lexeme));
                break;

            case IDENTIFIER:
            case NUMBER:
                operands.add(lexeme);
                break;

            case LITERAL:
                operands.add("'" + lexeme + "'");
                break;

            case NULL:
                if ("IS".equals(operator)) {
                    condition.setType(notFlag ?
                            ConditionNode.ConditionType.IS_NOT_NULL :
                            ConditionNode.ConditionType.IS_NULL);
                }
                break;

            case KEYWORD:
                if ("NOT".equals(lexeme)) {
                    notFlag = true;
                } else if ("LIKE".equals(lexeme)) {
                    operator = "LIKE";
                    condition.setOperator("$regex");
                } else if ("BETWEEN".equals(lexeme)) {
                    condition.setType(ConditionNode.ConditionType.BETWEEN);
                } else if ("IN".equals(lexeme)) {
                    condition.setType(IN);
                } else if ("EXISTS".equals(lexeme)) {
                    condition.setType(notFlag ?
                            NOT_EXISTS :
                            EXISTS);
                } else if ("IS".equals(lexeme)) {
                    operator = "IS";
                }
                break;
        }
    }

    private static void setSubqueryConditionType(ConditionNode condition,
                                                 String operator,
                                                 boolean notFlag) {

        if ("EXISTS".equals(operator) || "NOT".equals(operator)) {
            condition.setType(notFlag ? NOT_EXISTS : EXISTS);
        } else if ("IN".equals(operator)) {
            condition.setType(IN);
        } else {
            condition.setType(COMPARISON);
        }
    }

    private static List<Object> extractAttributes(Node attributesNode) {

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

    //Извлечение условий корреляции из подзапроса
    public static List<CorrelationCondition> extractCorrelations(Node subqueryNode) {
        List<CorrelationCondition> correlations = new ArrayList<>();

        // В реальной реализации нужно анализировать WHERE подзапроса
        // на наличие ссылок на внешние таблицы

        // Заглушка
        CorrelationCondition correlation = new CorrelationCondition();
        correlation.setOuterField("outer_field");
        correlation.setInnerField("inner_field");
        correlation.setOperator("=");

        correlations.add(correlation);

        return correlations;
    }

    public enum ConditionContext {
        WHERE,
        HAVING,
        JOIN,
        SELECT
    }
}
