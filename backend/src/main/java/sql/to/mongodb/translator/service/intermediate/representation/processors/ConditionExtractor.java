package sql.to.mongodb.translator.service.intermediate.representation.processors;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.model.Constant;
import sql.to.mongodb.translator.service.intermediate.representation.model.CorrelationCondition;
import sql.to.mongodb.translator.service.intermediate.representation.model.Field;
import sql.to.mongodb.translator.service.intermediate.representation.model.Subquery;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.*;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.math.BigDecimal;
import java.util.*;

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
        LinkNode.LinkType combineType = null;

        for (Node child : logicalNode.getChildren()) {
            if (child.getNodeType() == NodeType.LOGICAL_CHECK) {
                ConditionNode condition = extractLogicalCheck(child);
                if (condition != null) {
                    subConditions.add(condition);
                }
            } else if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;
                if ("AND".equals(lexeme)) {
                    combineType = LinkNode.LinkType.AND;
                } else if ("OR".equals(lexeme)) {
                    combineType = LinkNode.LinkType.OR;
                }
            }
        }

        if (subConditions.isEmpty()) {
            return null;
        }

        if (subConditions.size() == 1) {
            return subConditions.getFirst();
        }

        LinkNode combined = new LinkNode();
        combined.setType(combineType != null ? combineType : LinkNode.LinkType.AND);
        combined.getChildren().addAll(subConditions);
        return combined;
    }

    private ConditionNode extractLogicalCheck(Node logicalCheckNode) {
        if (logicalCheckNode.getChildren() == null) {
            return null;
        }

        boolean hasExists = false;
        boolean hasIn = false;
        boolean hasBetween = false;
        boolean hasIsNull = false;
        boolean hasNot = false;
        String operator = null;
        List<Expressionable> operands = new ArrayList<>();
        Node subqueryNode = null;

        for (Node child : logicalCheckNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                String lexeme = token.lexeme;

                if (token.category == Category.LOGICAL_OPERATOR) {
                    operator = lexeme;
                } else if (token.category == Category.KEYWORD) {
                    switch (lexeme) {
                        case "NOT":
                            hasNot = true;
                            break;
                        case "EXISTS":
                            hasExists = true;
                            break;
                        case "IN":
                            hasIn = true;
                            break;
                        case "BETWEEN":
                            hasBetween = true;
                            break;
                        case "IS":
                            break;
                        case "LIKE":
                            operator = "LIKE";
                            break;
                    }
                } else if (token.category == Category.NULL) {
                    hasIsNull = true;
                }
            } else if (child.getNodeType() == NodeType.QUERY) {
                subqueryNode = child;
            } else {
                Expressionable expr = buildExpression(child);
                if (expr != null) {
                    operands.add(expr);
                }
            }
        }

        // EXISTS (SELECT ...)
        if (hasExists) {
            ExistsCondition exists = new ExistsCondition();
            exists.setExists(!hasNot);
            // Подзапрос будет установлен позже через setSubqueryForExists
            return exists;
        }

        // IN (value1, value2, ...) или IN (SELECT ...)
        if (hasIn) {
            InCondition inCondition = new InCondition();

            if (subqueryNode != null) {
                // Это IN (SELECT ...) - подзапрос
                // Создаем Subquery, который будет заполнен позже
                Subquery subquery = new Subquery();
                // subquery.setSubqueryIR(...) будет установлено позже
                inCondition.getInValues().add(subquery);
            } else {
                // IN с константами/полями
                for (Expressionable expr : operands) {
                    // Фильтруем только поддерживаемые типы
                    if (isSupportedForIn(expr)) {
                        inCondition.getInValues().add(expr);
                    }
                }
            }

            return inCondition;
        }

        // BETWEEN start AND end
        if (hasBetween && operands.size() >= 2) {
            BetweenCondition between = new BetweenCondition();

            Expressionable startExpr = operands.get(0);
            Expressionable endExpr = operands.get(1);

            if (startExpr instanceof Constant c1 && c1.getType() == Constant.ConstantType.NUMBER) {
                between.setStart(new BigDecimal(c1.getValue().toString()));
            }
            if (endExpr instanceof Constant c2 && c2.getType() == Constant.ConstantType.NUMBER) {
                between.setEnd(new BigDecimal(c2.getValue().toString()));
            }

            return between;
        }

        // IS NULL / IS NOT NULL
        if (hasIsNull && !operands.isEmpty()) {
            NullCheck nullCheck = new NullCheck();
            nullCheck.setOperand(operands.getFirst());
            nullCheck.setNull(!hasNot);
            return nullCheck;
        }

        // Простое сравнение: field = value, field > value и т.д.
        if (operands.size() >= 2) {
            Comparison comparison = new Comparison();
            comparison.setField(operands.get(0));
            comparison.setOperator(operator != null ? operator : "=");
            comparison.setValue(operands.get(1));
            return comparison;
        }

        return null;
    }

    /**
     * Проверка, поддерживается ли выражение в IN
     * В вашей логике: Constant, Field, Subquery
     */
    private boolean isSupportedForIn(Expressionable expr) {
        return expr instanceof Constant ||
                expr instanceof Field ||
                expr instanceof Subquery;
    }

    private Expressionable buildExpression(Node node) {
        if (node == null) return null;

        switch (node.getNodeType()) {
            case TERMINAL:
                Token token = node.getToken();
                if (token.category == Category.IDENTIFIER) {
                    Field field = new Field();
                    field.setField(token.lexeme);
                    return field;
                } else if (token.category == Category.NUMBER) {
                    return Constant.ofNumber(token.lexeme);
                } else if (token.category == Category.LITERAL) {
                    return Constant.ofString(token.lexeme);
                }
                break;

            case IDENTIFIER:
                return buildField(node);

            case ARITHMETIC_EXP:
                // ArithmeticExpression не поддерживается в IN по вашей логике
                return null;

            case CASE:
                // CaseExpression не поддерживается в IN по вашей логике
                return null;

            case QUERY:
                return new Subquery();
        }

        return null;
    }

    private Field buildField(Node identifierNode) {
        return ExpressionBuilder.buildField(identifierNode);
    }

    /**
     * Создание и заполнение подзапроса для EXISTS
     */
    public ExistsCondition createExistsCondition(Node subqueryNode, SqlToMongoIR subqueryIR) {
        ExistsCondition exists = new ExistsCondition();
        exists.setExists(true);

        CorrelationSubquery correlationSubquery = new CorrelationSubquery();
        correlationSubquery.setSubqueryIR(subqueryIR);

        // Извлекаем корреляции
        List<CorrelationCondition> correlations = extractCorrelations(subqueryNode);
        correlationSubquery.getCorrelations().addAll(correlations);

        exists.setSubquery(correlationSubquery);
        return exists;
    }

    /**
     * Создание и заполнение подзапроса для IN
     */
    public InCondition createInConditionWithSubquery(Node subqueryNode, SqlToMongoIR subqueryIR, Expressionable leftOperand) {
        InCondition inCondition = new InCondition();

        // Создаем подзапрос с корреляциями
        Subquery subquery = new Subquery();
        subquery.setSubqueryIR(subqueryIR);

        // Добавляем подзапрос в inValues
        inCondition.getInValues().add(subquery);

        return inCondition;
    }

    /**
     * Извлечение корреляций для подзапроса
     */
    public List<CorrelationCondition> extractCorrelations(Node subqueryNode) {
        CorrelationAnalyzer analyzer = new CorrelationAnalyzer();
        analyzer.analyzeForCorrelations(subqueryNode, outerTables, outerAliases);

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

    public enum ConditionContext {
        WHERE, HAVING, JOIN, SELECT
    }
}