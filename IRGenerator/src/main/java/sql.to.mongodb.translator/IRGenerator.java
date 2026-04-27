package sql.to.mongodb.translator;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.IRGenerationException;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.GroupByField;
import sql.to.mongodb.translator.ir.SortField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;
import sql.to.mongodb.translator.ir.expression.Arithmetical;
import sql.to.mongodb.translator.ir.expression.CaseExpression;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinSubquery;
import sql.to.mongodb.translator.ir.join.JoinTable;
import sql.to.mongodb.translator.ir.join.Joinable;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.CaseProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.SubqueryProjection;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.processors.CaseBuilder;
import sql.to.mongodb.translator.processors.ConditionExtractor;
import sql.to.mongodb.translator.processors.ExpressionBuilder;
import sql.to.mongodb.translator.scanner.Token;

import java.util.*;

import static sql.to.mongodb.translator.processors.ExpressionBuilder.extractAlias;
import static sql.to.mongodb.translator.processors.ExpressionBuilder.extractFieldParts;
import static sql.to.mongodb.translator.processors.ExpressionBuilder.processAlias;
import static sql.to.mongodb.translator.scanner.Category.IDENTIFIER;

@Component
public class IRGenerator {

    public SqlToMongoIR generateIR(Node astRoot) throws IRGenerationException {
        return generateIR(astRoot, new HashSet<>(), new HashMap<>());
    }

    public SqlToMongoIR generateIR(Node astRoot,
                                   Set<String> outerTables,
                                   Map<String, String> outerAliases) throws IRGenerationException {

        if (astRoot == null || astRoot.getNodeType() != NodeType.QUERY) {
            throw new IRGenerationException("Invalid AST root node");
        }

        // Создаём новый контекст для каждого вызова
        GenerationContext ctx = new GenerationContext(
                new SqlToMongoIR(),
                new HashMap<>(),
                new Stack<>(),
                new HashSet<>(outerTables),
                new HashMap<>(outerAliases)
        );

        processQueryNode(astRoot, ctx);
        return ctx.ir;
    }

    // ==================== GenerationContext ====================

    public static class GenerationContext {
        public final SqlToMongoIR ir;
        public final Map<String, String> tableAliases;
        public final Stack<String> currentContext;
        public final Set<String> outerTables;
        public final Map<String, String> outerAliases;
        public final ConditionExtractor conditionExtractor;

        public GenerationContext(SqlToMongoIR ir,
                                 Map<String, String> tableAliases,
                                 Stack<String> currentContext,
                                 Set<String> outerTables,
                                 Map<String, String> outerAliases) {
            this.ir = ir;
            this.tableAliases = tableAliases;
            this.currentContext = currentContext;
            this.outerTables = outerTables;
            this.outerAliases = outerAliases;
            this.conditionExtractor = new ConditionExtractor(ir, outerTables, outerAliases);
        }
    }

    // ==================== Process Query ====================

    private void processQueryNode(Node queryNode,
                                  GenerationContext ctx) throws IRGenerationException {
        if (queryNode.getChildren() == null) return;
        for (Node child : queryNode.getChildren()) {
            processQueryChild(child, ctx);
        }
    }

    private void processQueryChild(Node child,
                                   GenerationContext ctx) throws IRGenerationException {
        switch (child.getNodeType()) {
            case TERMINAL -> processTerminalInQuery(child, ctx);
            case COLUMN_NAMES -> processColumnNames(child, ctx);
            case TABLE_NAMES -> processTableNames(child, ctx);
            case LOGICAL_CONDITION -> processConditionNode(child, ctx);
            case GROUP_BY -> processGroupBy(child, ctx);
            case ORDER_BY -> processOrderBy(child, ctx);
            case LIMIT -> processLimit(child, ctx);
            case OFFSET -> processOffset(child, ctx);
            default -> {
                if (child.getChildren() != null) {
                    for (Node grandChild : child.getChildren()) {
                        processQueryChild(grandChild, ctx);
                    }
                }
            }
        }
    }

    // ==================== Terminal ====================

    private void processTerminalInQuery(Node terminalNode, GenerationContext ctx) {
        Token token = terminalNode.getToken();
        if (token != null && "DISTINCT".equals(token.lexeme)) {
            ctx.ir.setDistinct(true);
        }
    }

    // ==================== Condition ====================

    private void processConditionNode(Node conditionNode, GenerationContext ctx) {
        ConditionExtractor.ConditionContext context = determineConditionContext(ctx);
        ConditionNode extractedCondition = ctx.conditionExtractor.extractCondition(
                conditionNode,
                this,
                ctx);

        if (extractedCondition != null) {
            switch (context) {
                case WHERE -> ctx.ir.setWhereCondition(extractedCondition);
                case HAVING -> {
                    ctx.ir.setHavingCondition(extractedCondition);
                    ctx.ir.setHasHaving(true);
                }
                default -> {
                }
            }
        }
    }

    private ConditionExtractor.ConditionContext determineConditionContext(GenerationContext ctx) {
        if (ctx.ir.isHasGroupBy() && ctx.ir.getHavingCondition() != null) {
            return ConditionExtractor.ConditionContext.HAVING;
        }
        return ConditionExtractor.ConditionContext.WHERE;
    }

    // ==================== Column Names / Projection ====================

    private void processColumnNames(Node columnNamesNode,
                                    GenerationContext ctx) throws IRGenerationException {
        if (columnNamesNode.getChildren() == null) return;

        int i = 0;
        List<Node> columns = columnNamesNode.getChildren();

        while (i < columns.size()) {
            switch (columns.get(i).getNodeType()) {
                case TERMINAL -> {
                    if ("*".equals(columns.get(i).getToken().lexeme)) {
                        processAllColumns(ctx);
                        ++i;
                    } else {
                        ProjectionField field = new ProjectionField();
                        field.setField(ExpressionBuilder.buildExpressionString(columns.get(i)));
                        i = processAlias(columns, i, field);
                        ctx.ir.getProjectionFields().add(field);
                    }
                }
                case IDENTIFIER -> {
                    ProjectionField field = new ProjectionField();
                    if (!extractFieldParts(columns.get(i), field)) {
                        i = processAlias(columns, i, field);
                        ctx.ir.getProjectionFields().add(field);
                    } else {
                        ++i;
                    }
                }
                case AGGREGATE -> {
                    AggregateProjection aggregate = ExpressionBuilder.buildAggregateFunction(columns.get(i));
                    if (aggregate != null) {
                        ctx.ir.getProjectionFields().add(aggregate);
                        ctx.ir.setHasAggregateFunctions(true);
                    }
                    ++i;
                }
                case ARITHMETIC_EXP -> {
                    Arithmetical arithmeticExpr = ExpressionBuilder.buildArithmeticExpression(columns.get(i));
                    if (arithmeticExpr != null) {
                        ArithmeticProjection arithmetic = new ArithmeticProjection();
                        arithmetic.setExpression(arithmeticExpr);
                        ++i;
                        i = processAlias(columns, i, arithmetic);
                        ctx.ir.getProjectionFields().add(arithmetic);
                        ctx.ir.setHasComplexProjections(true);
                    } else {
                        ++i;
                    }
                }
                case CASE -> {
                    CaseExpression caseExpr = parseCaseExpression(columns.get(i));
                    if (caseExpr != null) {
                        CaseProjection caseProjection = new CaseProjection();
                        caseProjection.setExpression(caseExpr);
                        i = processAlias(columns, i, caseProjection);
                        ctx.ir.getProjectionFields().add(caseProjection);
                        ctx.ir.setHasComplexProjections(true);
                    } else {
                        ++i;
                    }
                }
                case QUERY -> i = processSubqueryInProjection(columns, i, ctx);
                default -> {
                    ProjectionField defaultField = new ProjectionField();
                    defaultField.setField(ExpressionBuilder.buildExpressionString(columns.get(i)));
                    i = processAlias(columns, i, defaultField);
                    ctx.ir.getProjectionFields().add(defaultField);
                }
            }
        }
    }

    private void processAllColumns(GenerationContext ctx) {
        ProjectionField field = new ProjectionField();
        field.setField("*");
        if (!ctx.currentContext.isEmpty()) {
            field.setSource(ctx.currentContext.peek());
        }
        ctx.ir.getProjectionFields().add(field);
    }

    private int processSubqueryInProjection(List<Node> columns,
                                            int i,
                                            GenerationContext ctx) throws IRGenerationException {
        int result = i;

        SqlToMongoIR subqueryIR = generateIR(columns.get(i), ctx.outerTables, ctx.tableAliases);

        SubqueryProjection projection = new SubqueryProjection();
        projection.setSubqueryIR(subqueryIR);
        ++result;

        String alias = extractAlias(columns, result);
        if (alias != null) {
            result += 2;
            projection.setAlias(alias);
        }

        List<CorrelationCondition> correlations = ctx.conditionExtractor.extractCorrelations(columns.get(i));
        projection.getCorrelations().addAll(correlations);

        ctx.ir.getProjectionFields().add(projection);
        ctx.ir.setHasSubqueries(true);
        ctx.ir.setHasComplexProjections(true);

        return result;
    }

    // ==================== Case Expression ====================

    private CaseExpression parseCaseExpression(Node caseNode) throws IRGenerationException {
        if (caseNode.getChildren() == null) return null;

        CaseBuilder builder = CaseBuilder.create();
        List<Node> children = caseNode.getChildren();
        int i = 0;

        while (i < children.size()) {
            Node child = children.get(i);
            if (child.getNodeType() == NodeType.TERMINAL && "CASE".equals(child.getToken().lexeme)) {
                i++;
                break;
            }
            i++;
        }

        while (i < children.size()) {
            Node child = children.get(i);
            if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;
                if ("WHEN".equals(lexeme)) {
                    i++;
                    Expressionable condition = parseConditionFromNodes(children, i);
                    while (i < children.size()) {
                        Node condNode = children.get(i);
                        if (condNode.getNodeType() == NodeType.TERMINAL
                                && "THEN".equals(condNode.getToken().lexeme)) {
                            i++;
                            break;
                        }
                        i++;
                    }
                    Expressionable result = parseResultFromNodes(children, i);
                    while (i < children.size()) {
                        Node resNode = children.get(i);
                        if (resNode.getNodeType() == NodeType.TERMINAL &&
                                ("WHEN".equals(resNode.getToken().lexeme) ||
                                        "ELSE".equals(resNode.getToken().lexeme) ||
                                        "END".equals(resNode.getToken().lexeme)
                                )) {
                            break;
                        }
                        i++;
                    }
                    if (condition != null && result != null) {
                        builder.when(condition, result);
                    }
                    continue;
                } else if ("ELSE".equals(lexeme)) {
                    i++;
                    Expressionable elseExpr = parseResultFromNodes(children, i);
                    while (i < children.size()) {
                        Node endNode = children.get(i);
                        if (endNode.getNodeType() == NodeType.TERMINAL &&
                                "END".equals(endNode.getToken().lexeme)) {
                            break;
                        }
                        i++;
                    }
                    if (elseExpr != null) {
                        builder.otherwise(elseExpr);
                    }
                    break;
                } else if ("END".equals(lexeme)) {
                    break;
                }
            }
            i++;
        }
        return builder.build();
    }

    private Expressionable parseConditionFromNodes(List<Node> nodes, int startIndex) {
        if (startIndex >= nodes.size()) return null;
        StringBuilder conditionBuilder = new StringBuilder();
        int i = startIndex;
        while (i < nodes.size()) {
            Node node = nodes.get(i);
            if (node.getNodeType() == NodeType.TERMINAL && "THEN".equals(node.getToken().lexeme)) break;
            String expr = ExpressionBuilder.buildExpressionString(node);
            if (!expr.isEmpty()) conditionBuilder.append(expr).append(" ");
            i++;
        }
        String conditionStr = conditionBuilder.toString().trim();
        return conditionStr.isEmpty() ? null : parseExpressionString(conditionStr);
    }

    private Expressionable parseResultFromNodes(List<Node> nodes, int startIndex) {
        if (startIndex >= nodes.size()) return null;
        StringBuilder resultBuilder = new StringBuilder();
        int i = startIndex;
        while (i < nodes.size()) {
            Node node = nodes.get(i);
            if (node.getNodeType() == NodeType.TERMINAL) {
                String lexeme = node.getToken().lexeme;
                if ("WHEN".equals(lexeme) || "ELSE".equals(lexeme) || "END".equals(lexeme)) break;
            }
            String expr = ExpressionBuilder.buildExpressionString(node);
            if (!expr.isEmpty()) resultBuilder.append(expr).append(" ");
            i++;
        }
        String resultStr = resultBuilder.toString().trim();
        return resultStr.isEmpty() ? null : parseExpressionString(resultStr);
    }

    private Expressionable parseExpressionString(String expr) {
        if (expr == null || expr.trim().isEmpty()) return null;
        expr = expr.trim();
        if (expr.startsWith("'") && expr.endsWith("'")) {
            return Constant.ofString(expr.substring(1, expr.length() - 1));
        } else if (expr.contains(".")) {
            String[] parts = expr.split("\\.");
            if (parts.length == 2) {
                return new Field(parts[0], parts[1]);
            }
        } else {
            try {
                Double.parseDouble(expr);
                return Constant.ofNumber(expr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(expr);
            }
        }
        return new Field(expr);
    }

    // ==================== Group By ====================

    private void processGroupBy(Node groupByNode, GenerationContext ctx) {
        ctx.ir.setHasGroupBy(true);
        if (groupByNode.getChildren() != null) {
            for (Node child : groupByNode.getChildren()) {
                GroupByField field = extractFieldFromGroupBy(child);
                if (field != null) {
                    ctx.ir.getGroupByFields().add(field);
                }
            }
        }
    }

    private GroupByField extractFieldFromGroupBy(Node node) {
        if (node.getNodeType() == NodeType.TERMINAL) {
            return new GroupByField(node.getToken().lexeme);
        } else if (node.getNodeType() == NodeType.IDENTIFIER) {
            List<Node> parts = node.getChildren();
            return new GroupByField(parts.getFirst().getToken().lexeme, parts.getLast().getToken().lexeme);
        }
        return null;
    }

    // ==================== Order By ====================

    private void processOrderBy(Node orderByNode, GenerationContext ctx) {
        if (orderByNode.getChildren() == null) return;
        SortField currentField = null;
        Boolean currentDirection = null;
        for (Node child : orderByNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL && child.getToken().category != IDENTIFIER) {
                String lexeme = child.getToken().lexeme;
                if ("ASC".equals(lexeme)) {
                    if (currentField != null) {
                        addSortField(currentField, true, ctx);
                        currentField = null;
                    }
                    currentDirection = true;
                } else if ("DESC".equals(lexeme)) {
                    if (currentField != null) {
                        addSortField(currentField, false, ctx);
                        currentField = null;
                    }
                    currentDirection = false;
                }
            } else {
                currentField = extractFieldFromOrderBy(child);
            }
        }
        if (currentField != null) {
            addSortField(currentField, currentDirection != null ? currentDirection : true, ctx);
        }
    }

    private SortField extractFieldFromOrderBy(Node node) {
        List<Node> parts = node.getChildren();
        if (parts.isEmpty()) {
            return new SortField(node.getToken().lexeme);
        }
        return new SortField(parts.getFirst().getToken().lexeme, parts.getLast().getToken().lexeme);
    }

    private void addSortField(SortField field, boolean ascending, GenerationContext ctx) {
        field.setDirection(ascending);
        ctx.ir.getOrderBy().add(field);
    }

    // ==================== Limit / Offset ====================

    private void processLimit(Node limitNode, GenerationContext ctx) {
        if (limitNode.getChildren() == null) return;
        ctx.ir.setLimit(Integer.parseInt(limitNode.getChildren().getFirst().getToken().lexeme));
    }

    private void processOffset(Node offsetNode, GenerationContext ctx) {
        if (offsetNode.getChildren() == null) return;
        ctx.ir.setOffset(Integer.parseInt(offsetNode.getChildren().getFirst().getToken().lexeme));
    }

    // ==================== Table Names / JOIN ====================

    private void processTableNames(Node tableNamesNode,
                                   GenerationContext ctx) throws IRGenerationException {
        if (tableNamesNode.getChildren() == null) return;

        JoinInfo currentJoin = new JoinInfo();

        for (Node child : tableNamesNode.getChildren()) {
            switch (child.getNodeType()) {
                case TABLE -> {
                    Joinable operand = extractJoinableFromTable(child, ctx);
                    if (ctx.ir.getMainCollection() == null) {
                        currentJoin.setLeft(operand);
                        if (operand instanceof JoinTable table) {
                            ctx.ir.setMainCollection(table.getValue());
                            currentJoin.setLeft(operand);
                        } else if (operand instanceof JoinSubquery) {
                            ctx.ir.setMainCollection("subquery");
                        }
                    } else if (currentJoin.getLeft() == null) {
                        currentJoin.setLeft(operand);
                    } else {
                        currentJoin.setRight(operand);
                    }
                    if (operand.getAlias() != null) {
                        ctx.currentContext.push(operand.getAlias());
                        ctx.tableAliases.put(operand.getAlias(),
                                operand instanceof JoinTable t ? t.getValue() : "subquery");
                        ctx.ir.getAliases().put(operand.getAlias(),
                                operand instanceof JoinTable t ? t.getValue() : "subquery");
                    } else {
                        ctx.currentContext.push(operand instanceof JoinTable t ? t.getValue() : "subquery");
                    }
                    ctx.outerTables.add(operand instanceof JoinTable t ? t.getValue() : "subquery");
                }
                case TERMINAL, JOIN -> {
                    processJoin(child, currentJoin);
                    ctx.ir.setHasJoins(true);
                }
                case LOGICAL_CONDITION -> {
                    ConditionNode joinCondition = ctx.conditionExtractor.extractCondition(
                            child,
                            this,
                            ctx);
                    currentJoin.setJoinCondition(joinCondition);
                    ctx.ir.getJoins().add(currentJoin);
                    currentJoin = new JoinInfo(currentJoin.getRight());
                }
                case QUERY -> {
                    Joinable operand = processSubqueryAsJoinable(child, ctx);
                    if (ctx.ir.getMainCollection() == null) {
                        ctx.ir.setMainCollection("subquery");
                        if (operand.getAlias() != null) {
                            ctx.currentContext.push(operand.getAlias());
                            ctx.tableAliases.put(operand.getAlias(), "subquery");
                            ctx.ir.getAliases().put(operand.getAlias(), "subquery");
                        }
                    } else {
                        currentJoin.setRight(operand);
                    }
                }
            }
        }
    }

    private Joinable extractJoinableFromTable(Node tableNode,
                                              GenerationContext ctx) throws IRGenerationException {
        String tableName = null;
        String alias = null;
        for (Node child : tableNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                if (token.category == IDENTIFIER) {
                    if (tableName == null) tableName = token.lexeme;
                    else alias = token.lexeme;
                }
            } else if (child.getNodeType() == NodeType.QUERY) {
                return processSubqueryAsJoinable(child, ctx);
            }
        }
        JoinTable joinTable = new JoinTable();
        joinTable.setValue(tableName);
        joinTable.setAlias(alias);
        return joinTable;
    }

    private Joinable processSubqueryAsJoinable(Node subqueryNode,
                                               GenerationContext ctx) throws IRGenerationException {
        String alias = extractAlias(subqueryNode);
        SqlToMongoIR subqueryIR = generateIR(subqueryNode, ctx.outerTables, ctx.tableAliases);
        JoinSubquery joinSubquery = new JoinSubquery();
        joinSubquery.setSubqueryIR(subqueryIR);
        joinSubquery.setAlias(alias);
        if (alias != null) {
            ctx.tableAliases.put(alias, "subquery");
            ctx.ir.getAliases().put(alias, "subquery");
            ctx.currentContext.push(alias);
        }
        return joinSubquery;
    }

    private void processJoin(Node joinNode, JoinInfo joinInfo) {
        if (joinNode.getChildren() == null || joinNode.getChildren().isEmpty()) {
            joinInfo.setType(JoinInfo.JoinType.INNER);
            return;
        }

        switch (joinNode.getChildren().getFirst().getToken().lexeme) {
            case "JOIN", "INNER" -> joinInfo.setType(JoinInfo.JoinType.INNER);
            case "LEFT" -> joinInfo.setType(JoinInfo.JoinType.LEFT);
            case "RIGHT" -> joinInfo.setType(JoinInfo.JoinType.RIGHT);
            case "FULL" -> joinInfo.setType(JoinInfo.JoinType.FULL);
            case "CROSS" -> joinInfo.setType(JoinInfo.JoinType.CROSS);
        }

        if (joinInfo.getType() == null) joinInfo.setType(JoinInfo.JoinType.INNER);
    }
}