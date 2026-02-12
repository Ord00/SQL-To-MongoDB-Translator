package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.IRGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.details.*;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import static sql.to.mongodb.translator.service.intermediate.representation.ConditionExtractor.ConditionContext;
import static sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo.SubqueryType.SCALAR;

public class SqlToMongoIRGenerator {

    private final Node astRoot;
    private final SqlToMongoIR ir;
    private final Map<String, String> tableAliases = new HashMap<>();
    private final Stack<String> currentContext = new Stack<>();
    private final Set<String> outerTables = new HashSet<>();
    private final ConditionExtractor conditionExtractor;

    public SqlToMongoIRGenerator(Node astRoot) {
        this(astRoot, new HashSet<>(), new HashMap<>());
    }

    // Конструктор для подзапросов с информацией о внешних таблицах
    public SqlToMongoIRGenerator(Node astRoot, Set<String> outerTables, Map<String, String> outerAliases) {
        this.astRoot = astRoot;
        this.ir = new SqlToMongoIR();
        this.outerTables.addAll(outerTables);
        this.conditionExtractor = new ConditionExtractor(outerTables, outerAliases);
    }

    public SqlToMongoIR generateIR() throws IRGenerationException {
        if (astRoot == null || astRoot.getNodeType() != NodeType.QUERY) {
            throw new IRGenerationException("Invalid AST root node");
        }

        processQueryNode(astRoot);
        return ir;
    }

    private void processQueryNode(Node queryNode) throws IRGenerationException {
        if (queryNode.getChildren() == null) return;

        // Сначала собираем информацию о таблицах
        processQueryStructure(queryNode);

        // Затем обрабатываем остальные части
        for (Node child : queryNode.getChildren()) {
            processQueryChild(child);
        }
    }

    private void processQueryStructure(Node queryNode) throws IRGenerationException {
        // Находим FROM и таблицы
        for (Node child : queryNode.getChildren()) {
            if (child.getNodeType() == NodeType.TABLE_NAMES) {
                processTableNames(child);
                break;
            }
        }
    }

    private void processQueryChild(Node child) throws IRGenerationException {
        switch (child.getNodeType()) {
            case TERMINAL:
                processTerminalInQuery(child);
                break;
            case COLUMN_NAMES:
                processColumnNames(child);
                break;
            case LOGICAL_CONDITION:
                processConditionNode(child);
                break;
            case GROUP_BY:
                processGroupBy(child);
                break;
            case ORDER_BY:
                processOrderBy(child);
                break;
            case CASE:
                processCaseExpression(child);
                break;
            case AGGREGATE:
                processAggregateFunction(child);
                break;
            case ARITHMETIC_EXP:
                processArithmeticExpression(child);
                break;
            case QUERY:
                processSubqueryInProjection(child);
                break;
        }
    }

    private void processConditionNode(Node conditionNode) {
        // Определяем контекст на основе позиции в запросе
        ConditionContext context = determineConditionContext();

        ConditionNode extractedCondition = conditionExtractor.extractCondition(conditionNode);

        if (extractedCondition != null) {
            switch (context) {
                case WHERE:
                    ir.getWhereConditions().add(extractedCondition);
                    break;
                case HAVING:
                    ir.getHavingConditions().add(extractedCondition);
                    ir.setHasHaving(true);
                    break;
                case JOIN:
                    // JOIN условия обрабатываются в processTableNames
                    // Здесь мы их игнорируем
                    break;
                case SELECT:
                    // Для SELECT условий создаем проекционное поле
                    ProjectionField field = new ProjectionField();
                    field.setField(extractedCondition.toString());
                    ir.getProjectionFields().add(field);
                    break;
            }
        }
    }

    private ConditionContext determineConditionContext() {
        // Упрощенная логика: если есть GROUP BY и еще нет HAVING, то это HAVING
        // Иначе WHERE
        if (ir.isHasGroupBy() && ir.getHavingConditions().isEmpty()) {
            return ConditionContext.HAVING;
        }
        return ConditionContext.WHERE;
    }

    private void processTerminalInQuery(Node terminalNode) {
        Token token = terminalNode.getToken();
        if (token == null) return;

        if (token.lexeme.equals("DISTINCT")) {
            ir.setDistinct(true);
        }
    }

    private void processColumnNames(Node columnNamesNode) throws IRGenerationException {
        if (columnNamesNode.getChildren() == null) return;

        for (Node child : columnNamesNode.getChildren()) {
            switch (child.getNodeType()) {
                case TERMINAL:
                    if ("*".equals(child.getToken().lexeme)) {
                        processAllColumns();
                    }
                    break;
                case IDENTIFIER:
                    processIdentifier(child);
                    break;
                case AGGREGATE:
                    processAggregateFunction(child);
                    break;
                case ARITHMETIC_EXP:
                    processArithmeticExpression(child);
                    break;
                case CASE:
                    processCaseExpression(child);
                    break;
                case LOGICAL_CHECK:
                    processLogicalCheckInSelect(child);
                    break;
                case QUERY:
                    processSubqueryInProjection(child);
                    break;
            }
        }
    }

    private void processLogicalCheckInSelect(Node logicalCheckNode) {
        ConditionNode condition = conditionExtractor.extractCondition(
                logicalCheckNode);

        if (condition != null) {
            ProjectionField field = new ProjectionField();
            field.setField(ExpressionBuilder.buildExpression(logicalCheckNode));
            field.setAlias(extractAlias(logicalCheckNode));
            ir.getProjectionFields().add(field);
            ir.setHasComplexProjections(true);
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

    private void processIdentifier(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2)
            return;

        String tableOrAlias = extractTokenValue(identifierNode.getChildren().get(0));
        String column = extractTokenValue(identifierNode.getChildren().get(1));

        ProjectionField field = new ProjectionField();
        field.setSource(tableOrAlias);
        field.setField(column);

        if (identifierNode.getChildren().size() > 2) {
            String alias = extractAliasFromChildren(identifierNode.getChildren());
            field.setAlias(alias);
        }

        ir.getProjectionFields().add(field);
    }

    private void processAggregateFunction(Node aggregateNode) {
        ir.setHasAggregateFunctions(true);

        ProjectionField field = extractAggregateInfo(aggregateNode);
        ir.getProjectionFields().add(field);
    }

    private void processArithmeticExpression(Node arithNode) {
        ir.setHasComplexProjections(true);

        ProjectionField field = new ProjectionField();
        field.setField(ExpressionBuilder.buildExpression(arithNode));
        field.setAlias(extractAlias(arithNode));
        ir.getProjectionFields().add(field);
    }

    private void processCaseExpression(Node caseNode) {
        ir.setHasComplexProjections(true);

        ProjectionField field = new ProjectionField();
        field.setField(ExpressionBuilder.buildExpression(caseNode));
        field.setAlias(extractAlias(caseNode));
        ir.getProjectionFields().add(field);
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
        Boolean currentDirection = null; // true = ASC, false = DESC, null = не указано

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
                // Это поле для сортировки
                String field = extractFieldFromOrderBy(child);
                if (field != null) {
                    // Если есть предыдущее поле без направления, добавляем его с ASC по умолчанию
                    if (currentField != null) {
                        addSortField(currentField, true);
                    }
                    currentField = field;
                    // Направление для этого поля будет определено следующим токеном
                }
            }
        }

        // Обрабатываем последнее поле
        if (currentField != null) {
            // Используем указанное направление или ASC по умолчанию
            addSortField(currentField, currentDirection != null ? currentDirection : true);
        }
    }

    private void addSortField(String field, boolean ascending) {
        SortField sortField = new SortField();
        sortField.setField(field);
        sortField.setDirection(ascending ? "ASC" : "DESC");
        ir.getOrderBy().add(sortField);
    }

    private void processTableNames(Node tableNamesNode) throws IRGenerationException {
        if (tableNamesNode.getChildren() == null) return;

        JoinInfo currentJoin = null;

        for (int i = 0; i < tableNamesNode.getChildren().size(); i++) {
            Node child = tableNamesNode.getChildren().get(i);

            switch (child.getNodeType()) {
                case TABLE:
                    TableInfo tableInfo = processTable(child);

                    if (ir.getMainCollection() == null) {
                        // Первая таблица - основная коллекция
                        ir.setMainCollection(tableInfo.tableName);
                        currentContext.push(tableInfo.alias != null ? tableInfo.alias : tableInfo.tableName);

                        // Добавляем таблицу во внешний контекст для подзапросов
                        outerTables.add(tableInfo.tableName);
                        if (tableInfo.alias != null) {
                            tableAliases.put(tableInfo.alias, tableInfo.tableName);
                            ir.getAliases().put(tableInfo.alias, tableInfo.tableName);
                        }
                    } else if (currentJoin != null) {
                        currentJoin.setRightTable(tableInfo.tableName);
                        currentJoin.setRightAlias(tableInfo.alias);

                        if (tableInfo.alias != null) {
                            tableAliases.put(tableInfo.alias, tableInfo.tableName);
                        }
                    }
                    break;

                case JOIN:
                    currentJoin = processJoin(child);
                    ir.getJoins().add(currentJoin);
                    ir.setHasJoins(true);
                    break;

                case LOGICAL_CONDITION:
                    if (currentJoin != null) {
                        ConditionNode joinCondition = conditionExtractor.extractCondition(child);
                        currentJoin.setJoinCondition(joinCondition);
                    }
                    break;

                case QUERY:
                    processSubqueryInFrom(child);
                    break;
            }
        }
    }

    private TableInfo processTable(Node tableNode) {
        TableInfo info = new TableInfo();

        if (tableNode.getChildren() == null) return info;

        for (Node child : tableNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                if (token.category == Category.IDENTIFIER) {
                    if (info.tableName == null) {
                        info.tableName = token.lexeme;
                    } else {
                        info.alias = token.lexeme;
                    }
                }
            } else if (child.getNodeType() == NodeType.QUERY) {
                info.isSubquery = true;
                info.tableName = "subquery_" + System.identityHashCode(child);
            }
        }
        return info;
    }

    private JoinInfo processJoin(Node joinNode) {
        JoinInfo joinInfo = new JoinInfo();

        if (joinNode.getChildren() != null && !joinNode.getChildren().isEmpty()) {
            Node firstChild = joinNode.getChildren().getFirst();
            if (firstChild.getNodeType() == NodeType.TERMINAL) {
                String joinType = firstChild.getToken().lexeme;

                switch (joinType) {
                    case "JOIN":
                    case "INNER":
                        joinInfo.setType(JoinInfo.JoinType.INNER);
                        break;
                    case "LEFT":
                        joinInfo.setType(JoinInfo.JoinType.LEFT);
                        break;
                    case "RIGHT":
                        joinInfo.setType(JoinInfo.JoinType.RIGHT);
                        break;
                }
            }
        }
        return joinInfo;
    }

    private SubqueryInfo processSubquery(Node subqueryNode) {
        ir.setHasSubqueries(true);

        // Создаем генератор для подзапроса с информацией о внешних таблицах
        SqlToMongoIRGenerator subqueryGenerator = new SqlToMongoIRGenerator(
                subqueryNode, outerTables, tableAliases);

        try {
            SqlToMongoIR subqueryIR = subqueryGenerator.generateIR();

            SubqueryInfo subqueryInfo = new SubqueryInfo();
            subqueryInfo.setType(SCALAR);
            subqueryInfo.setSubqueryIR(subqueryIR);

            // Используем conditionExtractor для извлечения корреляций
            List<CorrelationCondition> correlations = conditionExtractor.extractCorrelations(subqueryNode);
            if (!correlations.isEmpty()) {
                ir.setHasCorrelatedSubqueries(true);
                subqueryInfo.setCorrelations(correlations);
            }

            return subqueryInfo;

        } catch (IRGenerationException e) {
            return null;
        }
    }

    private void processSubqueryInProjection(Node subqueryNode) {
        SubqueryInfo subqueryInfo = processSubquery(subqueryNode);
        if (subqueryInfo != null) {
            // Добавляем подзапрос в IR и создаем проекционное поле
            ir.getSubqueries().add(subqueryInfo);

            ProjectionField field = new ProjectionField();
            field.setField("subquery_" + ir.getSubqueries().size());
            field.setAlias(extractAlias(subqueryNode));
            ir.getProjectionFields().add(field);
            ir.setHasComplexProjections(true);
        }
    }

    private void processSubqueryInFrom(Node subqueryNode) {
        String alias = extractSubqueryAlias(subqueryNode);

        SubqueryInfo subqueryInfo = processSubquery(subqueryNode);
        if (subqueryInfo != null && alias != null) {
            tableAliases.put(alias, "subquery");
            ir.getAliases().put(alias, "subquery");
            currentContext.push(alias);
        }
    }

    // ========== Вспомогательные методы ==========

    private ProjectionField extractAggregateInfo(Node aggregateNode) {
        ProjectionField field = new ProjectionField();
        String functionCall = ExpressionBuilder.buildExpression(aggregateNode);

        field.setField(functionCall);
        field.setAlias(extractAlias(aggregateNode));

        if (!currentContext.isEmpty()) {
            field.setSource(currentContext.peek());
        }

        return field;
    }

    private String extractTokenValue(Node node) {
        if (node.getNodeType() == NodeType.TERMINAL && node.getToken() != null) {
            return node.getToken().lexeme;
        }
        return null;
    }

    private String extractIdentifierString(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2) {
            return "";
        }

        String table = extractTokenValue(identifierNode.getChildren().get(0));
        String column = extractTokenValue(identifierNode.getChildren().get(1));

        return table + "." + column;
    }

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

    private String extractAliasFromChildren(List<Node> children) {
        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            if (child.getNodeType() == NodeType.TERMINAL &&
                    "AS".equals(child.getToken().lexeme) &&
                    i + 1 < children.size()) {
                Node aliasNode = children.get(i + 1);
                if (aliasNode.getNodeType() == NodeType.TERMINAL) {
                    return aliasNode.getToken().lexeme;
                }
            }
        }
        return null;
    }

    private String extractFieldFromGroupBy(Node node) {
        if (node.getNodeType() == NodeType.TERMINAL) {
            return node.getToken().lexeme;
        } else if (node.getNodeType() == NodeType.IDENTIFIER) {
            return extractIdentifierString(node);
        }
        return null;
    }

    private String extractFieldFromOrderBy(Node node) {
        return extractFieldFromGroupBy(node);
    }

    private String extractSubqueryAlias(Node subqueryNode) {
        return extractAlias(subqueryNode);
    }

    private static class TableInfo {
        String tableName;
        String alias;
        boolean isSubquery = false;
    }
}