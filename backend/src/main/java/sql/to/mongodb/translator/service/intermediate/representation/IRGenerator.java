package sql.to.mongodb.translator.service.intermediate.representation;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.IRGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.model.*;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.*;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.*;
import sql.to.mongodb.translator.service.intermediate.representation.model.join.*;
import sql.to.mongodb.translator.service.intermediate.representation.model.projection.*;
import sql.to.mongodb.translator.service.intermediate.representation.processors.ConditionExtractor;
import sql.to.mongodb.translator.service.intermediate.representation.processors.ExpressionBuilder;
import sql.to.mongodb.translator.service.intermediate.representation.processors.CaseBuilder;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.*;

import static sql.to.mongodb.translator.service.intermediate.representation.processors.ConditionExtractor.ConditionContext;

@Component
public class IRGenerator {

    private SqlToMongoIR ir;
    private final Map<String, String> tableAliases = new HashMap<>();
    private final Stack<String> currentContext = new Stack<>();
    private final Set<String> outerTables = new HashSet<>();
    private ConditionExtractor conditionExtractor;

    private final ThreadLocal<GenerationState> currentState = new ThreadLocal<>();

    private void initialize(Set<String> parentTables, Map<String, String> parentAliases) {
        this.ir = new SqlToMongoIR();
        this.tableAliases.clear();
        this.currentContext.clear();
        this.outerTables.clear();
        this.outerTables.addAll(parentTables);
        this.conditionExtractor = new ConditionExtractor(parentTables, parentAliases);
    }

    public SqlToMongoIR generateIR(Node astRoot) throws IRGenerationException {
        return generateIR(astRoot, new HashSet<>(), new HashMap<>());
    }

    public SqlToMongoIR generateIR(Node astRoot,
                                   Set<String> outerTables,
                                   Map<String, String> outerAliases) throws IRGenerationException {

        GenerationState previousState = currentState.get();

        try {
            initialize(outerTables, outerAliases);

            currentState.set(new GenerationState(
                    ir,
                    tableAliases,
                    currentContext,
                    this.outerTables,
                    conditionExtractor));

            if (astRoot == null || astRoot.getNodeType() != NodeType.QUERY) {
                throw new IRGenerationException("Invalid AST root node");
            }

            processQueryNode(astRoot);
            return ir;

        } finally {
            if (previousState != null) {
                currentState.set(previousState);
            } else {
                currentState.remove();
            }
        }
    }

    private void processQueryNode(Node queryNode) throws IRGenerationException {
        if (queryNode.getChildren() == null) return;

        processQueryStructure(queryNode);

        for (Node child : queryNode.getChildren()) {
            processQueryChild(child);
        }
    }

    private void processQueryStructure(Node queryNode) throws IRGenerationException {
        for (Node child : queryNode.getChildren()) {
            if (child.getNodeType() == NodeType.TABLE_NAMES) {
                processTableNames(child);
                break;
            }
        }
    }

    private void processQueryChild(Node child) throws IRGenerationException {
        switch (child.getNodeType()) {
            case TERMINAL -> processTerminalInQuery(child);
            case COLUMN_NAMES -> processColumnNames(child);
            case LOGICAL_CONDITION -> processConditionNode(child);
            case GROUP_BY -> processGroupBy(child);
            case ORDER_BY -> processOrderBy(child);
            default -> {
                if (child.getChildren() != null) {
                    for (Node grandChild : child.getChildren()) {
                        processQueryChild(grandChild);
                    }
                }
            }
        }
    }

    private void processTerminalInQuery(Node terminalNode) {
        Token token = terminalNode.getToken();
        if (token != null && "DISTINCT".equals(token.lexeme)) {
            ir.setDistinct(true);
        }
    }

    private void processConditionNode(Node conditionNode) {
        ConditionContext context = determineConditionContext();
        ConditionNode extractedCondition = conditionExtractor.extractCondition(conditionNode);

        if (extractedCondition != null) {
            switch (context) {
                case WHERE -> ir.getWhereConditions().add(extractedCondition);
                case HAVING -> {
                    ir.getHavingConditions().add(extractedCondition);
                    ir.setHasHaving(true);
                }
                default -> {}
            }
        }
    }

    private ConditionContext determineConditionContext() {
        if (ir.isHasGroupBy() && ir.getHavingConditions().isEmpty()) {
            return ConditionContext.HAVING;
        }
        return ConditionContext.WHERE;
    }

    private void processColumnNames(Node columnNamesNode) throws IRGenerationException {
        if (columnNamesNode.getChildren() == null) return;

        for (Node child : columnNamesNode.getChildren()) {
            processProjection(child);
        }
    }

    private void processAllColumns() {
        ProjectionField field = new ProjectionField();
        field.setField("*");
        if (!currentContext.isEmpty()) {
            field.setSource(currentContext.peek());
        }
        ir.getProjectionFields().add(field);
    }

    private void processProjection(Node projectionNode) throws IRGenerationException {
        switch (projectionNode.getNodeType()) {
            case TERMINAL -> {
                if ("*".equals(projectionNode.getToken().lexeme)) {
                    processAllColumns();
                } else {
                    ProjectionField field = new ProjectionField();
                    field.setField(ExpressionBuilder.buildExpressionString(projectionNode));
                    field.setAlias(extractAlias(projectionNode));
                    ir.getProjectionFields().add(field);
                }
            }
            case IDENTIFIER -> {
                ProjectionField field = ExpressionBuilder.buildFieldProjection(projectionNode);
                ir.getProjectionFields().add(field);
            }
            case AGGREGATE -> {
                AggregateProjection aggregate = ExpressionBuilder.buildAggregateFunction(projectionNode);
                if (aggregate != null) {
                    ir.getProjectionFields().add(aggregate);
                    ir.setHasAggregateFunctions(true);
                }
            }
            case ARITHMETIC_EXP -> {
                Arithmetical arithmeticExpr = ExpressionBuilder.buildArithmeticExpression(projectionNode);
                if (arithmeticExpr != null) {
                    ArithmeticProjection arithmetic = new ArithmeticProjection();
                    arithmetic.setExpression(arithmeticExpr);
                    arithmetic.setAlias(extractAlias(projectionNode));
                    ir.getProjectionFields().add(arithmetic);
                    ir.setHasComplexProjections(true);
                }
            }
            case CASE -> {
                CaseExpression caseExpr = parseCaseExpression(projectionNode);
                if (caseExpr != null) {
                    CaseProjection caseProjection = new CaseProjection();
                    caseProjection.setExpression(caseExpr);
                    caseProjection.setAlias(extractAlias(projectionNode));
                    ir.getProjectionFields().add(caseProjection);
                    ir.setHasComplexProjections(true);
                }
            }
            case QUERY -> processSubqueryInProjection(projectionNode);
            default -> {
                ProjectionField defaultField = new ProjectionField();
                defaultField.setField(ExpressionBuilder.buildExpressionString(projectionNode));
                defaultField.setAlias(extractAlias(projectionNode));
                ir.getProjectionFields().add(defaultField);
            }
        }
    }

    private CaseExpression parseCaseExpression(Node caseNode) throws IRGenerationException {
        if (caseNode.getChildren() == null) {
            return null;
        }

        CaseBuilder builder = CaseBuilder.create();

        List<Node> children = caseNode.getChildren();
        int i = 0;

        // Пропускаем "CASE"
        while (i < children.size()) {
            Node child = children.get(i);
            if (child.getNodeType() == NodeType.TERMINAL &&
                    "CASE".equals(child.getToken().lexeme)) {
                i++;
                break;
            }
            i++;
        }

        // Обрабатываем WHEN ... THEN ... пары
        while (i < children.size()) {
            Node child = children.get(i);

            if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;

                if ("WHEN".equals(lexeme)) {
                    i++;
                    Expressionable condition = parseConditionFromNodes(children, i);

                    while (i < children.size()) {
                        Node condNode = children.get(i);
                        if (condNode.getNodeType() == NodeType.TERMINAL &&
                                "THEN".equals(condNode.getToken().lexeme)) {
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
                                        "END".equals(resNode.getToken().lexeme))) {
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
                            i++;
                            break;
                        }
                        i++;
                    }

                    if (elseExpr != null) {
                        builder.otherwise(elseExpr);
                    }
                    break;

                } else if ("END".equals(lexeme)) {
                    i++;
                    break;
                }
            }
            i++;
        }

        return builder.build();
    }

    private Expressionable parseConditionFromNodes(List<Node> nodes, int startIndex) {
        if (startIndex >= nodes.size()) {
            return null;
        }

        StringBuilder conditionBuilder = new StringBuilder();
        int i = startIndex;

        while (i < nodes.size()) {
            Node node = nodes.get(i);

            if (node.getNodeType() == NodeType.TERMINAL &&
                    "THEN".equals(node.getToken().lexeme)) {
                break;
            }

            String expr = ExpressionBuilder.buildExpressionString(node);
            if (!expr.isEmpty()) {
                conditionBuilder.append(expr).append(" ");
            }
            i++;
        }

        String conditionStr = conditionBuilder.toString().trim();
        if (conditionStr.isEmpty()) {
            return null;
        }

        return parseExpressionString(conditionStr);
    }

    private Expressionable parseResultFromNodes(List<Node> nodes, int startIndex) {
        if (startIndex >= nodes.size()) {
            return null;
        }

        StringBuilder resultBuilder = new StringBuilder();
        int i = startIndex;

        while (i < nodes.size()) {
            Node node = nodes.get(i);

            if (node.getNodeType() == NodeType.TERMINAL) {
                String lexeme = node.getToken().lexeme;
                if ("WHEN".equals(lexeme) || "ELSE".equals(lexeme) || "END".equals(lexeme)) {
                    break;
                }
            }

            String expr = ExpressionBuilder.buildExpressionString(node);
            if (!expr.isEmpty()) {
                resultBuilder.append(expr).append(" ");
            }
            i++;
        }

        String resultStr = resultBuilder.toString().trim();
        if (resultStr.isEmpty()) {
            return null;
        }

        return parseExpressionString(resultStr);
    }

    private Expressionable parseExpressionString(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            return null;
        }

        expr = expr.trim();

        if (expr.startsWith("'") && expr.endsWith("'")) {
            String value = expr.substring(1, expr.length() - 1);
            return Constant.ofString(value);
        }

        try {
            Double.parseDouble(expr);
            return Constant.ofNumber(expr);
        } catch (NumberFormatException e) {
            // не число
        }

        if (expr.contains(".")) {
            String[] parts = expr.split("\\.");
            if (parts.length == 2) {
                Field field = new Field();
                field.setSource(parts[0]);
                field.setField(parts[1]);
                return field;
            }
        } else {
            Field field = new Field();
            field.setField(expr);
            return field;
        }

        return null;
    }

    private void processGroupBy(Node groupByNode) {
        ir.setHasGroupBy(true);
        ir.setRequiresAggregation(true);

        if (groupByNode.getChildren() != null) {
            for (Node child : groupByNode.getChildren()) {
                String field = extractFieldFromGroupBy(child);
                if (field != null && !field.isEmpty()) {
                    ir.getGroupByFields().add(field);
                }
            }
        }
    }

    private void processOrderBy(Node orderByNode) {
        if (orderByNode.getChildren() == null) return;

        String currentField = null;
        Boolean currentDirection = null;

        for (Node child : orderByNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;
                if ("ASC".equals(lexeme)) {
                    if (currentField != null) {
                        addSortField(currentField, true);
                        currentField = null;
                    }
                    currentDirection = true;
                } else if ("DESC".equals(lexeme)) {
                    if (currentField != null) {
                        addSortField(currentField, false);
                        currentField = null;
                    }
                    currentDirection = false;
                }
            } else {
                String field = extractFieldFromOrderBy(child);
                if (field != null) {
                    if (currentField != null) {
                        addSortField(currentField, true);
                    }
                    currentField = field;
                }
            }
        }

        if (currentField != null) {
            addSortField(currentField, currentDirection != null ? currentDirection : true);
        }
    }

    private void addSortField(String field, boolean ascending) {
        SortField sortField = new SortField();
        sortField.setField(field);
        sortField.setDirection(ascending);
        ir.getOrderBy().add(sortField);
    }

    private void processTableNames(Node tableNamesNode) throws IRGenerationException {
        if (tableNamesNode.getChildren() == null) return;

        JoinInfo currentJoin = null;
        Joinable lastOperand = null;

        for (Node child : tableNamesNode.getChildren()) {
            switch (child.getNodeType()) {
                case TABLE -> {
                    Joinable operand = extractJoinableFromTable(child);

                    if (ir.getMainCollection() == null) {
                        if (operand instanceof JoinTable table) {
                            ir.setMainCollection(table.getValue());
                        } else if (operand instanceof JoinSubquery) {
                            ir.setMainCollection("subquery");
                        }
                        if (operand.getAlias() != null) {
                            currentContext.push(operand.getAlias());
                            tableAliases.put(operand.getAlias(), operand instanceof JoinTable t ? t.getValue() : "subquery");
                            ir.getAliases().put(operand.getAlias(), operand instanceof JoinTable t ? t.getValue() : "subquery");
                        } else {
                            currentContext.push(operand instanceof JoinTable t ? t.getValue() : "subquery");
                        }
                        outerTables.add(operand instanceof JoinTable t ? t.getValue() : "subquery");
                    } else if (currentJoin != null) {
                        currentJoin.setRight(operand);
                    }
                    lastOperand = operand;
                }
                case JOIN -> {
                    if (currentJoin != null) {
                        ir.getJoins().add(currentJoin);
                    }
                    currentJoin = processJoin(child);
                    if (lastOperand != null) {
                        currentJoin.setLeft(lastOperand);
                    }
                    ir.setHasJoins(true);
                }
                case LOGICAL_CONDITION -> {
                    if (currentJoin != null) {
                        ConditionNode joinCondition = conditionExtractor.extractCondition(child);
                        currentJoin.setJoinCondition(joinCondition);
                    }
                }
                case QUERY -> {
                    Joinable operand = processSubqueryAsJoinable(child);
                    if (ir.getMainCollection() == null) {
                        ir.setMainCollection("subquery");
                        if (operand.getAlias() != null) {
                            currentContext.push(operand.getAlias());
                            tableAliases.put(operand.getAlias(), "subquery");
                            ir.getAliases().put(operand.getAlias(), "subquery");
                        }
                    } else if (currentJoin != null) {
                        currentJoin.setRight(operand);
                    }
                    lastOperand = operand;
                }
            }
        }

        if (currentJoin != null) {
            ir.getJoins().add(currentJoin);
        }
    }

    private Joinable extractJoinableFromTable(Node tableNode) throws IRGenerationException {
        String tableName = null;
        String alias = null;

        for (Node child : tableNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                if (token.category == Category.IDENTIFIER) {
                    if (tableName == null) {
                        tableName = token.lexeme;
                    } else {
                        alias = token.lexeme;
                    }
                }
            } else if (child.getNodeType() == NodeType.QUERY) {
                return processSubqueryAsJoinable(child);
            }
        }

        JoinTable joinTable = new JoinTable();
        joinTable.setValue(tableName);
        joinTable.setAlias(alias);
        return joinTable;
    }

    private Joinable processSubqueryAsJoinable(Node subqueryNode) throws IRGenerationException {
        String alias = extractAlias(subqueryNode);

        SqlToMongoIR subqueryIR = generateIR(subqueryNode, outerTables, tableAliases);

        JoinSubquery joinSubquery = new JoinSubquery();
        joinSubquery.setSubqueryIR(subqueryIR);
        joinSubquery.setAlias(alias);

        if (alias != null) {
            tableAliases.put(alias, "subquery");
            ir.getAliases().put(alias, "subquery");
            currentContext.push(alias);
        }

        return joinSubquery;
    }

    private JoinInfo processJoin(Node joinNode) {
        JoinInfo joinInfo = new JoinInfo();

        if (joinNode.getChildren() == null || joinNode.getChildren().isEmpty()) {
            // По умолчанию INNER JOIN
            joinInfo.setType(JoinInfo.JoinType.INNER);
            return joinInfo;
        }

        // Проверяем детей узла JOIN
        // Структура может быть:
        // 1. [TERMINAL|(KEYWORD|JOIN)] - для INNER JOIN или просто JOIN
        // 2. [TERMINAL|(KEYWORD|RIGHT), TERMINAL|(KEYWORD|JOIN)] - для RIGHT JOIN
        // 3. [TERMINAL|(KEYWORD|LEFT), TERMINAL|(KEYWORD|JOIN)] - для LEFT JOIN

        for (Node child : joinNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;

                switch (lexeme) {
                    case "JOIN":
                        // Если до JOIN не было LEFT/RIGHT, то это INNER JOIN
                        if (joinInfo.getType() == null) {
                            joinInfo.setType(JoinInfo.JoinType.INNER);
                        }
                        break;
                    case "INNER":
                        joinInfo.setType(JoinInfo.JoinType.INNER);
                        break;
                    case "LEFT":
                        joinInfo.setType(JoinInfo.JoinType.LEFT);
                        break;
                    case "RIGHT":
                        joinInfo.setType(JoinInfo.JoinType.RIGHT);
                        break;
                    case "FULL":
                        joinInfo.setType(JoinInfo.JoinType.FULL);
                        break;
                    case "CROSS":
                        joinInfo.setType(JoinInfo.JoinType.CROSS);
                        break;
                    default:
                        break;
                }
            }
        }

        // Если тип не определен, по умолчанию INNER
        if (joinInfo.getType() == null) {
            joinInfo.setType(JoinInfo.JoinType.INNER);
        }

        return joinInfo;
    }

    private void processSubqueryInProjection(Node subqueryNode) throws IRGenerationException {
        SqlToMongoIR subqueryIR = generateIR(subqueryNode, outerTables, tableAliases);

        SubqueryProjection projection = new SubqueryProjection();
        projection.setSubqueryIR(subqueryIR);
        projection.setAlias(extractAlias(subqueryNode));

        ir.getProjectionFields().add(projection);
        ir.setHasSubqueries(true);
        ir.setHasComplexProjections(true);
    }

    private void processSubqueryInCondition(Node subqueryNode, ConditionNode parentCondition) throws IRGenerationException {
        SqlToMongoIR subqueryIR = generateIR(subqueryNode, outerTables, tableAliases);

        Subquery subquery = new Subquery();
        subquery.setSubqueryIR(subqueryIR);

        // Извлекаем корреляции
        List<CorrelationCondition> correlations = conditionExtractor.extractCorrelations(subqueryNode);

        if (parentCondition instanceof ExistsCondition exists) {
            CorrelationSubquery correlationSubquery = new CorrelationSubquery();
            correlationSubquery.setSubqueryIR(subqueryIR);
            correlationSubquery.getCorrelations().addAll(correlations);
            exists.setSubquery(correlationSubquery);
            if (!correlations.isEmpty()) {
                ir.setHasCorrelatedSubqueries(true);
            }
        } else if (parentCondition instanceof InCondition inCondition) {
            inCondition.getInValues().add(subquery);
            if (!correlations.isEmpty()) {
                ir.setHasCorrelatedSubqueries(true);
            }
        } else if (parentCondition instanceof Comparison comparison) {
            // Сравнение с подзапросом
            comparison.setValue(subquery);
            if (!correlations.isEmpty()) {
                ir.setHasCorrelatedSubqueries(true);
            }
        }

        ir.setHasSubqueries(true);
    }

    private void processSubqueryInFrom(Node subqueryNode) throws IRGenerationException {
        String alias = extractAlias(subqueryNode);
        if (alias == null) {
            throw new IRGenerationException("Subquery in FROM must have an alias");
        }

        SqlToMongoIR subqueryIR = generateIR(subqueryNode, outerTables, tableAliases);

        JoinSubquery subqueryOperand = new JoinSubquery();
        subqueryOperand.setSubqueryIR(subqueryIR);
        subqueryOperand.setAlias(alias);

        if (ir.getMainCollection() == null) {
            ir.setMainCollection("subquery");
        } else {
            JoinInfo join = new JoinInfo();
            join.setType(JoinInfo.JoinType.INNER);
            join.setRight(subqueryOperand);
            ir.getJoins().add(join);
            ir.setHasJoins(true);
        }

        tableAliases.put(alias, "subquery");
        ir.getAliases().put(alias, "subquery");
        currentContext.push(alias);
    }

    private void processSubqueriesInCondition(Node node, ConditionNode parentCondition) {
        if (node == null) return;

        if (node.getNodeType() == NodeType.QUERY) {
            try {
                processSubqueryInCondition(node, parentCondition);
            } catch (IRGenerationException e) {
                // логирование ошибки
            }
        }

        if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                processSubqueriesInCondition(child, parentCondition);
            }
        }
    }

    // ========== Вспомогательные методы ==========

    private String extractAlias(Node node) {
        if (node.getChildren() != null) {
            for (int i = 0; i < node.getChildren().size(); i++) {
                Node child = node.getChildren().get(i);
                if (child.getNodeType() == NodeType.TERMINAL &&
                        "AS".equals(child.getToken().lexeme) &&
                        i + 1 < node.getChildren().size()) {
                    Node aliasNode = node.getChildren().get(i + 1);
                    if (aliasNode.getNodeType() == NodeType.TERMINAL) {
                        return aliasNode.getToken().lexeme;
                    }
                }
            }
        }
        return null;
    }

    private String extractFieldFromGroupBy(Node node) {
        if (node.getNodeType() == NodeType.TERMINAL) {
            return node.getToken().lexeme;
        } else if (node.getNodeType() == NodeType.IDENTIFIER) {
            return ExpressionBuilder.buildIdentifierString(node);
        }
        return null;
    }

    private String extractFieldFromOrderBy(Node node) {
        return extractFieldFromGroupBy(node);
    }

    private record GenerationState(SqlToMongoIR ir,
                                   Map<String, String> tableAliases,
                                   Stack<String> currentContext,
                                   Set<String> outerTables,
                                   ConditionExtractor conditionExtractor) {

        private GenerationState(SqlToMongoIR ir,
                                Map<String, String> tableAliases,
                                Stack<String> currentContext,
                                Set<String> outerTables,
                                ConditionExtractor conditionExtractor) {
            this.ir = ir;
            this.tableAliases = new HashMap<>(tableAliases);
            this.currentContext = new Stack<>();
            this.currentContext.addAll(currentContext);
            this.outerTables = new HashSet<>(outerTables);
            this.conditionExtractor = conditionExtractor;
        }
    }
}