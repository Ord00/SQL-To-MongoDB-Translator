package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.IRGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.intermediate.representation.details.JoinInfo;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SortField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static sql.to.mongodb.translator.service.intermediate.representation.ConditionExtractor.ConditionContext;

public class SqlToMongoIRGenerator {

    private final Node astRoot;
    private final SqlToMongoIR ir;
    private final Map<String, String> tableAliases = new HashMap<>();
    private final Stack<String> currentContext = new Stack<>();
    private final List<SubqueryInfo> nestedSubqueries = new ArrayList<>();

    public SqlToMongoIRGenerator(Node astRoot) {
        this.astRoot = astRoot;
        this.ir = new SqlToMongoIR();
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

        boolean hasWhere = false;
        boolean hasHaving = false;

        for (Node child : queryNode.getChildren()) {
            switch (child.getNodeType()) {
                case TERMINAL:
                    processTerminalInQuery(child);
                    break;
                case COLUMN_NAMES:
                    processColumnNames(child);
                    break;
                case TABLE_NAMES:
                    processTableNames(child);
                    break;
                case LOGICAL_CONDITION:
                    // Используем ConditionExtractor для обработки условий
                    ConditionNode condition = ConditionExtractor.extractCondition(child,
                            determineConditionContext(child, hasWhere, hasHaving));

                    if (condition != null) {
                        if (!hasWhere) {
                            ir.getWhereConditions().add(condition);
                            hasWhere = true;
                        } else if (!hasHaving) {
                            ir.getHavingConditions().add(condition);
                            ir.setHasHaving(true);
                            hasHaving = true;
                        }
                    }
                    break;
                case GROUP_BY:
                    processGroupBy(child);
                    break;
                case ORDER_BY:
                    processOrderBy(child);
                    break;
                case CASE:
                    processCaseExpression(child, true);
                    break;
                case AGGREGATE:
                    processAggregateFunction(child, true);
                    break;
                case ARITHMETIC_EXP:
                    processArithmeticExpression(child, true);
                    break;
                case QUERY:
                    processSubquery(child, SubqueryInfo.SubqueryType.SCALAR);
                    break;
            }
        }
    }

    private ConditionContext determineConditionContext(Node conditionNode,
                                                       boolean hasWhere,
                                                       boolean hasHaving) {
        if (!hasWhere) {
            return ConditionContext.WHERE;
        } else if (!hasHaving) {
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
                    processAggregateFunction(child, true);
                    break;
                case ARITHMETIC_EXP:
                    processArithmeticExpression(child, true);
                    break;
                case CASE:
                    processCaseExpression(child, true);
                    break;
                case LOGICAL_CHECK:
                    // Используем ConditionExtractor для обработки логических проверок
                    ConditionNode condition = ConditionExtractor.extractCondition(
                            child,
                            ConditionContext.SELECT);
                    if (condition != null) {
                        // Добавляем как проекционное поле
                        ProjectionField field = new ProjectionField();
                        field.setField(condition.toString());
                        field.setAlias(extractAlias(child));
                        ir.getProjectionFields().add(field);
                    }
                    break;
                case QUERY:
                    processSubquery(child, SubqueryInfo.SubqueryType.SCALAR);
                    break;
            }
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

    private void processAggregateFunction(Node aggregateNode, boolean inSelect) {

        ir.setHasAggregateFunctions(true);

        if (inSelect) {
            ir.setRequiresAggregation(true);
        }

        ProjectionField field = extractAggregateInfo(aggregateNode);
        ir.getProjectionFields().add(field);
    }

    private void processArithmeticExpression(Node arithNode, boolean inSelect) {

        if (inSelect) {
            ir.setHasComplexProjections(true);
        }

        ProjectionField field = new ProjectionField();
        field.setField(ExpressionBuilder.buildExpression(arithNode));
        field.setAlias(extractAlias(arithNode));
        ir.getProjectionFields().add(field);
    }

    private void processCaseExpression(Node caseNode, boolean inSelect) {

        if (inSelect) {
            ir.setHasComplexProjections(true);
        }

        ProjectionField field = new ProjectionField();
        field.setField(ExpressionBuilder.buildExpression(caseNode));
        field.setAlias(extractAlias(caseNode));
        ir.getProjectionFields().add(field);
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

                        if (tableInfo.alias != null) {
                            tableAliases.put(tableInfo.alias, tableInfo.tableName);
                            ir.getAliases().put(tableInfo.alias, tableInfo.tableName);
                        }
                    } else if (currentJoin != null) {
                        // Правая таблица для джойна
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
                        // Используем ConditionExtractor для условий JOIN
                        ConditionNode joinCondition = ConditionExtractor.extractCondition(
                                child,
                                ConditionContext.JOIN);
                        currentJoin.setJoinCondition(joinCondition);
                    }
                    break;

                case QUERY:
                    // Подзапрос в FROM
                    processSubqueryInFrom(child);
                    break;

                case TERMINAL:
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
                    case "JOIN", "INNER":
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

        if (orderByNode.getChildren() != null) {

            boolean ascending = true;
            String currentField = null;

            for (Node child : orderByNode.getChildren()) {

                if (child.getNodeType() == NodeType.TERMINAL) {

                    String lexeme = child.getToken().lexeme;
                    if ("ASC".equals(lexeme)) {

                        ascending = true;
                        // Если есть поле, создаем SortField
                        if (currentField != null) {
                            addSortField(currentField, ascending);
                            currentField = null;
                        }
                    } else if ("DESC".equals(lexeme)) {
                        ascending = false;
                        // Если есть поле, создаем SortField
                        if (currentField != null) {
                            addSortField(currentField, ascending);
                            currentField = null;
                        }
                    }
                } else {
                    String field = extractFieldFromOrderBy(child);
                    if (field != null) {
                        // Сохраняем поле, ждем направления
                        currentField = field;
                    }
                }
            }

            // Обрабатываем последнее поле если не указано ASC/DESC
            if (currentField != null) {
                addSortField(currentField, ascending);
            }
        }
    }

    private void addSortField(String field, boolean ascending) {
        SortField sortField = new SortField();
        sortField.setField(field);
        sortField.setDirection(ascending ? "ASC" : "DESC");
        ir.getOrderBy().add(sortField);
    }

    private SubqueryInfo processSubquery(Node subqueryNode, SubqueryInfo.SubqueryType type) {

        ir.setHasSubqueries(true);
        SqlToMongoIRGenerator subqueryGenerator = new SqlToMongoIRGenerator(subqueryNode);

        try {
            SqlToMongoIR subqueryIR = subqueryGenerator.generateIR();

            SubqueryInfo subqueryInfo = new SubqueryInfo();
            subqueryInfo.setType(type);
            subqueryInfo.setSubqueryIR(subqueryIR);

            // Используем ConditionExtractor для извлечения корреляций
            List<CorrelationCondition> correlations = ConditionExtractor.extractCorrelations(subqueryNode);
            if (!correlations.isEmpty()) {
                ir.setHasCorrelatedSubqueries(true);
                subqueryInfo.setCorrelations(correlations);
            }

            nestedSubqueries.add(subqueryInfo);
            return subqueryInfo;

        } catch (IRGenerationException e) {
            // Ошибка обработки подзапроса
            return null;
        }
    }

    private void processSubqueryInFrom(Node subqueryNode) {
        // Подзапрос в FROM должен иметь алиас
        String alias = extractSubqueryAlias(subqueryNode);

        SubqueryInfo subqueryInfo = processSubquery(subqueryNode, SubqueryInfo.SubqueryType.SCALAR);
        if (subqueryInfo != null && alias != null) {

            tableAliases.put(alias, "subquery");
            ir.getAliases().put(alias, "subquery");
            currentContext.push(alias);
        }
    }

    // ========== Вспомогательные методы извлечения информации ==========

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
        // Ищем AS в определении подзапроса
        return extractAlias(subqueryNode);
    }

    private static class TableInfo {
        String tableName;
        String alias;
        boolean isSubquery = false;
    }
}