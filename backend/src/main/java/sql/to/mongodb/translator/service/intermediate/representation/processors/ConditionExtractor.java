package sql.to.mongodb.translator.service.intermediate.representation.processors;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.IRGenerator;
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

public record ConditionExtractor(SqlToMongoIR ir,
                                 Set<String> outerTables,
                                 Map<String, String> outerAliases) {

    public ConditionExtractor(SqlToMongoIR ir,
                              Set<String> outerTables,
                              Map<String, String> outerAliases) {
        this.ir = ir;
        this.outerTables = outerTables != null ? outerTables : new HashSet<>();
        this.outerAliases = outerAliases != null ? outerAliases : new HashMap<>();
    }

    public ConditionNode extractCondition(Node logicalNode, IRGenerator irGenerator) {
        if (logicalNode == null || logicalNode.getChildren() == null) {
            return null;
        }

        List<ConditionNode> subConditions = new ArrayList<>();
        LinkNode.LinkType combineType = null;

        for (Node child : logicalNode.getChildren()) {
            if (child.getNodeType() == NodeType.LOGICAL_CHECK) {
                ConditionNode condition = extractLogicalCheck(child, irGenerator);
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

    private ConditionNode extractLogicalCheck(Node logicalCheckNode, IRGenerator irGenerator) {
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
        Expressionable leftOperand = null;

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
                    if (leftOperand == null && !hasIn && !hasExists) {
                        leftOperand = expr;
                    }
                }
            }
        }

        // EXISTS (SELECT ...)
        if (hasExists && subqueryNode != null && irGenerator != null) {
            return createExistsCondition(subqueryNode, irGenerator);
        }

        // IN (SELECT ...)
        if (hasIn && subqueryNode != null && irGenerator != null) {
            return createInConditionWithSubquery(subqueryNode, irGenerator, leftOperand);
        }

        // IN (value1, value2, ...)
        if (hasIn && !operands.isEmpty()) {
            InCondition inCondition = new InCondition();
            for (Expressionable expr : operands) {
                if (isSupportedForIn(expr)) {
                    inCondition.getInValues().add(expr);
                }
            }
            return inCondition;
        }

        // BETWEEN start AND end
        if (hasBetween && operands.size() >= 2) {
            BetweenCondition between = new BetweenCondition();
            between.setOperand(operands.getFirst());

            Expressionable startExpr = operands.size() > 1 ? operands.get(1) : null;
            Expressionable endExpr = operands.size() > 2 ? operands.get(2) : null;

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

        // Простое сравнение
        if (operands.size() >= 2) {
            Comparison comparison = new Comparison();
            comparison.setField(operands.get(0));
            comparison.setOperator(operator != null ? operator : "=");
            comparison.setValue(operands.get(1));
            return comparison;
        }

        return null;
    }

    private ExistsCondition createExistsCondition(Node subqueryNode, IRGenerator irGenerator) {
        ExistsCondition exists = new ExistsCondition();
        exists.setExists(true);

        try {
            SqlToMongoIR subqueryIR = irGenerator.generateIR(subqueryNode, outerTables, outerAliases);
            CorrelationSubquery correlationSubquery = new CorrelationSubquery();
            correlationSubquery.setSubqueryIR(subqueryIR);

            List<CorrelationCondition> correlations = extractCorrelations(subqueryNode);
            correlationSubquery.getCorrelations().addAll(correlations);

            exists.setSubquery(correlationSubquery);

            if (ir != null && !correlations.isEmpty()) {
                ir.setHasCorrelatedSubqueries(true);
                ir.setHasSubqueries(true);
            }
        } catch (Exception e) {
            // Логирование ошибки
        }

        return exists;
    }

    private InCondition createInConditionWithSubquery(Node subqueryNode, IRGenerator irGenerator, Expressionable leftOperand) {
        InCondition inCondition = new InCondition();

        if (leftOperand != null) {
            inCondition.setLeftOperand(leftOperand);
        }

        try {
            SqlToMongoIR subqueryIR = irGenerator.generateIR(subqueryNode, outerTables, outerAliases);
            List<CorrelationCondition> correlations = extractCorrelations(subqueryNode);

            if (!correlations.isEmpty()) {
                CorrelationSubquery correlationSubquery = new CorrelationSubquery();
                correlationSubquery.setSubqueryIR(subqueryIR);
                correlationSubquery.getCorrelations().addAll(correlations);
                inCondition.getInValues().add(correlationSubquery);

                if (ir != null) {
                    ir.setHasCorrelatedSubqueries(true);
                }
            } else {
                Subquery subquery = new Subquery();
                subquery.setSubqueryIR(subqueryIR);
                inCondition.getInValues().add(subquery);
            }

            if (ir != null) {
                ir.setHasSubqueries(true);
            }
        } catch (Exception e) {
            // Логирование ошибки
        }

        return inCondition;
    }

    private boolean isSupportedForIn(Expressionable expr) {
        return expr instanceof Constant || expr instanceof Field || expr instanceof Subquery;
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
            case CASE:
                return null;
            case QUERY:
                return new Subquery();
        }
        return null;
    }

    private Field buildField(Node identifierNode) {
        return ExpressionBuilder.buildField(identifierNode);
    }

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