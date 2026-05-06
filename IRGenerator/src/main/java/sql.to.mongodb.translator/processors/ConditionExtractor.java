package sql.to.mongodb.translator.processors;

import sql.to.mongodb.translator.IRGenerator;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.condition.BetweenCondition;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;
import sql.to.mongodb.translator.ir.condition.ExistsCondition;
import sql.to.mongodb.translator.ir.condition.InCondition;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.ir.condition.NullCheck;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Category;
import sql.to.mongodb.translator.scanner.Token;

import java.math.BigDecimal;
import java.util.*;

import static sql.to.mongodb.translator.processors.ExpressionBuilder.buildAggregateExpression;

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

    /**
     * Извлечение условия с учетом приоритетов операторов и скобок
     * Использует алгоритм рекурсивного спуска
     */
    public ConditionNode extractCondition(Node logicalNode,
                                          IRGenerator irGenerator,
                                          IRGenerator.GenerationContext ctx) {
        if (logicalNode == null || logicalNode.getChildren() == null) {
            return null;
        }

        // Преобразуем AST в линейную последовательность токенов
        List<Object> tokens = linearizeCondition(logicalNode);

        // Парсим с учетом приоритетов
        return parseCondition(tokens, irGenerator, ctx);
    }

    /**
     * Преобразование AST условия в линейную последовательность токенов
     */
    private List<Object> linearizeCondition(Node logicalNode) {
        List<Object> tokens = new ArrayList<>();
        linearizeRecursive(logicalNode, tokens);
        return tokens;
    }

    private void linearizeRecursive(Node node, List<Object> tokens) {
        if (node == null) return;

        if (node.getNodeType() == NodeType.LOGICAL_CHECK) {
            // LOGICAL_CHECK - это атомарное условие, сохраняем его как узел
            tokens.add(node);
        } else if (node.getNodeType() == NodeType.TERMINAL) {
            String lexeme = node.getToken().lexeme;
            if ("AND".equals(lexeme) || "OR".equals(lexeme)) {
                tokens.add(lexeme);
            } else if ("(".equals(lexeme) || ")".equals(lexeme)) {
                tokens.add(lexeme);
            }
        } else if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                linearizeRecursive(child, tokens);
            }
        }
    }

    /**
     * Парсинг условий с учетом приоритетов операторов
     * Грамматика:
     * Expression -> OrExpression
     * OrExpression -> AndExpression { 'OR' AndExpression }
     * AndExpression -> PrimaryExpression { 'AND' PrimaryExpression }
     * PrimaryExpression -> '(' Expression ')' | LOGICAL_CHECK
     */
    private ConditionNode parseCondition(List<Object> tokens,
                                         IRGenerator irGenerator,
                                         IRGenerator.GenerationContext ctx) {
        if (tokens.isEmpty()) {
            return null;
        }

        return parseOrExpression(tokens, new int[]{0}, irGenerator, ctx);
    }

    /**
     * OR имеет наименьший приоритет
     */
    private ConditionNode parseOrExpression(List<Object> tokens,
                                            int[] pos,
                                            IRGenerator irGenerator,
                                            IRGenerator.GenerationContext ctx) {
        ConditionNode left = parseAndExpression(tokens, pos, irGenerator, ctx);

        while (pos[0] < tokens.size()) {
            Object token = tokens.get(pos[0]);
            if (!"OR".equals(token)) {
                break;
            }
            pos[0]++; // пропускаем OR

            ConditionNode right = parseAndExpression(tokens, pos, irGenerator, ctx);

            LinkNode orNode = new LinkNode();
            orNode.setType(LinkNode.LinkType.OR);
            orNode.getChildren().add(left);
            orNode.getChildren().add(right);
            left = orNode;
        }

        return left;
    }

    /**
     * AND имеет средний приоритет
     */
    private ConditionNode parseAndExpression(List<Object> tokens,
                                             int[] pos,
                                             IRGenerator irGenerator,
                                             IRGenerator.GenerationContext ctx) {
        ConditionNode left = parsePrimaryExpression(tokens, pos, irGenerator, ctx);

        while (pos[0] < tokens.size()) {
            Object token = tokens.get(pos[0]);
            if (!"AND".equals(token)) {
                break;
            }
            pos[0]++; // пропускаем AND

            ConditionNode right = parsePrimaryExpression(tokens, pos, irGenerator, ctx);

            LinkNode andNode = new LinkNode();
            andNode.setType(LinkNode.LinkType.AND);
            andNode.getChildren().add(left);
            andNode.getChildren().add(right);
            left = andNode;
        }

        return left;
    }

    /**
     * Первичное выражение - это либо условие в скобках, либо атомарное условие
     */
    private ConditionNode parsePrimaryExpression(List<Object> tokens,
                                                 int[] pos,
                                                 IRGenerator irGenerator,
                                                 IRGenerator.GenerationContext ctx) {
        if (pos[0] >= tokens.size()) {
            return null;
        }

        Object token = tokens.get(pos[0]);

        // Обработка скобок
        if ("(".equals(token)) {
            pos[0]++; // пропускаем '('
            ConditionNode expr = parseOrExpression(tokens, pos, irGenerator, ctx);

            // Ожидаем закрывающую скобку
            if (pos[0] < tokens.size()
                    && tokens.get(pos[0]) instanceof String
                    && ")".equals(tokens.get(pos[0]))) {
                pos[0]++; // пропускаем ')'
            }
            return expr;
        }

        // Атомарное условие (LOGICAL_CHECK)
        if (token instanceof Node node && node.getNodeType() == NodeType.LOGICAL_CHECK) {
            pos[0]++;
            return extractLogicalCheck(node, irGenerator, ctx);
        }

        return null;
    }

    /**
     * Извлечение атомарного условия из LOGICAL_CHECK узла
     */
    private ConditionNode extractLogicalCheck(Node logicalCheckNode,
                                              IRGenerator irGenerator,
                                              IRGenerator.GenerationContext ctx) {
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
        Node attributesNode = null;
        Expressionable leftOperand = null;

        for (Node child : logicalCheckNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                String lexeme = token.lexeme;

                switch (token.category) {
                    case LOGICAL_OPERATOR -> operator = lexeme;
                    case NOT -> hasNot = true;
                    case LOGICAL_EXPRESSION -> {
                        switch (lexeme) {
                            case "EXISTS" -> hasExists = true;
                            case "IN" -> hasIn = true;
                            case "BETWEEN" -> hasBetween = true;
                            case "LIKE" -> operator = "LIKE";
                        }
                    }
                    case NULL -> hasIsNull = true;
                    default -> {
                        Expressionable expr = buildExpression(child, ctx);
                        if (expr != null) {
                            operands.add(expr);
                            if (leftOperand == null && !hasIn && !hasExists) {
                                leftOperand = expr;
                            }
                        }
                    }
                }
            } else if (child.getNodeType() == NodeType.QUERY) {
                subqueryNode = child;
            } else if (child.getNodeType() == NodeType.ATTRIBUTES) {
                attributesNode = child;
            } else {
                Expressionable expr = buildExpression(child, ctx);
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
            return createExistsCondition(subqueryNode, irGenerator, hasNot);
        }

        // IN (SELECT ...)
        if (hasIn) {
            InCondition inCondition = new InCondition();

            // Устанавливаем левый операнд (поле или выражение до IN)
            if (leftOperand != null) {
                inCondition.setOperand(leftOperand);
            }

            // Обрабатываем ATTRIBUTES (значения после IN)
            if (attributesNode != null) {
                List<Expressionable> attributes = extractAttributes(attributesNode, irGenerator);
                inCondition.setInValues(attributes);
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
            comparison.setOperand(operands.get(0));
            comparison.setOperator(operator != null ? operator : "=");
            comparison.setValue(operands.get(1));
            return comparison;
        }

        return null;
    }

    /**
     * Извлечение значений из узла ATTRIBUTES
     * ATTRIBUTES может содержать:
     * - TERMINAL (числа, строки)
     * - IDENTIFIER (поля)
     * - ARITHMETIC_EXP (арифметические выражения)
     * - QUERY (подзапросы)
     */
    private List<Expressionable> extractAttributes(Node attributesNode,
                                                   IRGenerator irGenerator) {
        List<Expressionable> values = new ArrayList<>();

        if (attributesNode.getChildren() == null) {
            return values;
        }

        for (Node child : attributesNode.getChildren()) {
            Expressionable expr = buildAttributeExpression(child, irGenerator);
            if (expr != null) {
                values.add(expr);
            }
        }

        return values;
    }

    /**
     * Построение выражения из элемента ATTRIBUTES
     */
    private Expressionable buildAttributeExpression(Node node, IRGenerator irGenerator) {
        if (node == null) return null;

        switch (node.getNodeType()) {
            case TERMINAL:
                Token token = node.getToken();
                if (token.category == Category.NUMBER) {
                    return Constant.ofNumber(token.lexeme);
                } else if (token.category == Category.LITERAL) {
                    return Constant.ofString(token.lexeme);
                } else if (token.category == Category.IDENTIFIER) {
                    return new Field(token.lexeme);
                }
                break;

            case IDENTIFIER:
                return buildField(node);

            case ARITHMETIC_EXP:
                return ExpressionBuilder.buildArithmeticExpression(node);

            case QUERY:
                // Для подзапроса в IN нужно создать CorrelationSubquery, если есть корреляции
                SqlToMongoIR subqueryIR = irGenerator.generateIR(node, outerTables, outerAliases);

                // Извлекаем корреляции для этого подзапроса
                List<CorrelationCondition> correlations = extractCorrelations(node);

                if (!correlations.isEmpty()) {
                    CorrelationSubquery correlationSubquery = new CorrelationSubquery();
                    correlationSubquery.setSubqueryIR(subqueryIR);
                    correlationSubquery.getCorrelations().addAll(correlations);
                    return correlationSubquery;
                } else {
                    return new Subquery(subqueryIR);
                }

            default:
                // Если узел имеет детей, рекурсивно обрабатываем
                if (node.getChildren() != null && node.getChildren().size() == 1) {
                    return buildAttributeExpression(node.getChildren().getFirst(), irGenerator);
                }
                break;
        }

        return null;
    }

    private ExistsCondition createExistsCondition(Node subqueryNode,
                                                  IRGenerator irGenerator,
                                                  boolean hasNot) {
        ExistsCondition exists = new ExistsCondition();
        exists.setExists(!hasNot);

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

        return exists;
    }

    private Expressionable buildExpression(Node node, IRGenerator.GenerationContext ctx) {
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
                return ExpressionBuilder.buildArithmeticExpression(node);
            case AGGREGATE:
                return buildAggregateExpression(node, ctx);
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
        WHERE,
        HAVING,
        JOIN,
        SELECT
    }
}