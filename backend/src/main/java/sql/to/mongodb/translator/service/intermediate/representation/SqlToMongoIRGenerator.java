package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.IRGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.details.*;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.*;

public class SqlToMongoIRGenerator {

    private final Node astRoot;
    private final SqlToMongoIR ir;
    private final Map<String, String> tableAliases = new HashMap<>();
    private final Stack<String> currentContext = new Stack<>();
    private final List<SubqueryInfo> nestedSubqueries = new ArrayList<>();
    private final Set<String> outerTables = new HashSet<>();

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
                    // ????? ?????????? ???????? - WHERE ??? HAVING
                    if (!hasWhere) {
                        processWhereCondition(child);
                        hasWhere = true;
                    } else if (!hasHaving) {
                        processHavingCondition(child);
                        hasHaving = true;
                    } else {
                        processLogicalCondition(child, ConditionContext.WHERE);
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
                    // Может быть в подзапросах
                    processLogicalCheck(child, ConditionContext.SELECT);
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
        field.setField(buildExpressionString(arithNode));
        field.setAlias(extractAlias(arithNode));
        ir.getProjectionFields().add(field);
    }

    private void processCaseExpression(Node caseNode, boolean inSelect) {

        if (inSelect) {
            ir.setHasComplexProjections(true);
        }

        ProjectionField field = new ProjectionField();
        field.setField(buildCaseExpressionString(caseNode));
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
                        ConditionNode joinCondition = processJoinCondition(child);
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

    private ConditionNode processJoinCondition(Node conditionNode) throws IRGenerationException {
        return processLogicalCondition(conditionNode, ConditionContext.JOIN);
    }

    private void processWhereCondition(Node conditionNode) throws IRGenerationException {
        ConditionNode condition = processLogicalCondition(conditionNode, ConditionContext.WHERE);
        ir.getWhereConditions().add(condition);
    }

    private void processHavingCondition(Node conditionNode) throws IRGenerationException {
        ConditionNode condition = processLogicalCondition(conditionNode, ConditionContext.HAVING);
        ir.getHavingConditions().add(condition);
        ir.setHasHaving(true);
    }

    private ConditionNode processLogicalCondition(Node logicalNode, ConditionContext context)
            throws IRGenerationException {

        if (logicalNode.getChildren() == null || logicalNode.getChildren().isEmpty()) {
            return null;
        }

        List<ConditionNode> conditions = new ArrayList<>();
        String logicalCombine = null;

        for (Node child : logicalNode.getChildren()) {

            if (child.getNodeType() == NodeType.LOGICAL_CHECK) {

                ConditionNode condition = processLogicalCheck(child, context);
                if (condition != null) {
                    conditions.add(condition);
                }
            } else if (child.getNodeType() == NodeType.TERMINAL) {

                String lexeme = child.getToken().lexeme;
                if ("AND".equals(lexeme) || "OR".equals(lexeme)) {
                    logicalCombine = lexeme;
                }
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }

        if (conditions.size() == 1) {
            return conditions.getFirst();
        }

        // Объединение условия
        ConditionNode combined = new ConditionNode();
        combined.setType("AND".equals(logicalCombine) ?
                ConditionNode.ConditionType.AND : ConditionNode.ConditionType.OR);
        combined.getChildren().addAll(conditions);

        return combined;
    }

    private ConditionNode processLogicalCheck(Node logicalCheckNode,
                                              ConditionContext context) throws IRGenerationException {

        if (logicalCheckNode.getChildren() == null || logicalCheckNode.getChildren().isEmpty()) {
            return null;
        }

        ConditionNode condition = new ConditionNode();
        List<Node> operands = new ArrayList<>();
        String operator = null;
        boolean notFlag = false;

        for (Node child : logicalCheckNode.getChildren()) {

            switch (child.getNodeType()) {
                case TERMINAL:
                    Token token = child.getToken();
                    String lexeme = token.lexeme;

                    switch (token.category) {
                        case LOGICAL_OPERATOR:
                            operator = lexeme;
                            break;
                        case LOGICAL_COMBINE:
                            break;
                        case IDENTIFIER:
                        case NUMBER:
                        case LITERAL:
                            operands.add(child);
                            break;
                        case NULL:
                            condition.setType(ConditionNode.ConditionType.IS_NULL);
                            break;
                        case KEYWORD:
                            if ("NOT".equals(lexeme)) {
                                notFlag = true;
                            } else if ("LIKE".equals(lexeme)) {
                                operator = "LIKE";
                            } else if ("BETWEEN".equals(lexeme)) {
                                condition.setType(ConditionNode.ConditionType.BETWEEN);
                            } else if ("IN".equals(lexeme)) {
                                condition.setType(ConditionNode.ConditionType.IN);
                            } else if ("EXISTS".equals(lexeme)) {
                                condition.setType(notFlag ?
                                        ConditionNode.ConditionType.NOT_EXISTS :
                                        ConditionNode.ConditionType.EXISTS);
                            } else if ("IS".equals(lexeme)) {
                                operator = "IS";
                            }
                            break;
                    }
                    break;

                case ARITHMETIC_EXP:
                    condition.setField(buildExpressionString(child));
                    break;

                case AGGREGATE:
                    if (context == ConditionContext.HAVING) {
                        ir.setHasAggregateFunctions(true);
                    }
                    condition.setField(buildAggregateString(child));
                    break;

                case QUERY:
                    SubqueryInfo subqueryInfo = processSubquery(child,
                            determineSubqueryTypeFromCondition(operator, notFlag));

                    if (subqueryInfo != null) {

                        condition.setValue(subqueryInfo);

                        if (subqueryInfo.getType() == SubqueryInfo.SubqueryType.EXISTS) {

                            condition.setType(notFlag ?
                                    ConditionNode.ConditionType.NOT_EXISTS :
                                    ConditionNode.ConditionType.EXISTS);
                        } else if (subqueryInfo.getType() == SubqueryInfo.SubqueryType.IN) {
                            condition.setType(ConditionNode.ConditionType.IN);
                        }
                    }
                    break;

                case ATTRIBUTES:
                    // Список значений для IN
                    processAttributesForIn(child, condition);
                    break;
            }
        }

        // Устанавливаем тип условия если еще не установлен
        if (condition.getType() == null) {

            if (operator != null) {

                condition.setType(ConditionNode.ConditionType.COMPARISON);
                condition.setOperator(operator);
            } else if (operands.size() >= 2) {
                // Пытаемся определить поле и значение
                condition.setType(ConditionNode.ConditionType.COMPARISON);
                condition.setField(extractOperandValue(operands.get(0)));
                condition.setValue(extractOperandValue(operands.get(1)));
            }
        }
        return condition;
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
                        // ???? ???? ????, ??????? SortField
                        if (currentField != null) {
                            addSortField(currentField, ascending);
                            currentField = null;
                        }
                    } else if ("DESC".equals(lexeme)) {
                        ascending = false;
                        // ???? ???? ????, ??????? SortField
                        if (currentField != null) {
                            addSortField(currentField, ascending);
                            currentField = null;
                        }
                    }
                } else {
                    String field = extractFieldFromOrderBy(child);
                    if (field != null) {
                        // ????????? ????, ???? ???????? ???????????
                        currentField = field;
                    }
                }
            }

            // ????????? ?????????? ????, ???? ?? ???? ASC/DESC
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

            if (hasCorrelations(subqueryNode)) {
                ir.setHasCorrelatedSubqueries(true);
                subqueryInfo.setCorrelations(extractCorrelationConditions(subqueryNode));
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

    private void processAttributesForIn(Node attributesNode, ConditionNode condition) {

        if (attributesNode.getChildren() == null) return;

        List<Object> values = new ArrayList<>();
        for (Node child : attributesNode.getChildren()) {

            if (child.getNodeType() == NodeType.TERMINAL) {

                values.add(extractTokenValue(child));

            } else if (child.getNodeType() == NodeType.QUERY) {
                // Подзапрос в IN
                SubqueryInfo subquery = processSubquery(child, SubqueryInfo.SubqueryType.IN);
                if (subquery != null) {
                    condition.setValue(subquery);
                    return;
                }
            }
        }
        condition.setValue(values);
    }

    // ========== Вспомогательные методы извлечения информации ==========

    private ProjectionField extractColumnExpressionInfo(Node columnExprNode) {
        ProjectionField field = new ProjectionField();

        if (columnExprNode.getChildren() != null) {
            String source = null;
            String column = null;

            for (Node child : columnExprNode.getChildren()) {
                if (child.getNodeType() == NodeType.IDENTIFIER) {
                    if (child.getChildren() != null && child.getChildren().size() >= 2) {
                        source = extractTokenValue(child.getChildren().get(0));
                        column = extractTokenValue(child.getChildren().get(1));
                    }
                } else if (child.getNodeType() == NodeType.TERMINAL &&
                        child.getToken().category == Category.IDENTIFIER) {
                    column = child.getToken().lexeme;
                }
            }

            field.setSource(source != null ? source :
                    (!currentContext.isEmpty() ? currentContext.peek() : null));
            field.setField(column);
            field.setAlias(extractAlias(columnExprNode));
        }

        return field;
    }

    private ProjectionField extractAggregateInfo(Node aggregateNode) {
        ProjectionField field = new ProjectionField();
        StringBuilder functionCall = new StringBuilder();

        if (aggregateNode.getChildren() != null) {
            for (Node child : aggregateNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    functionCall.append(child.getToken().lexeme);
                } else if (child.getNodeType() == NodeType.IDENTIFIER) {
                    functionCall.append(extractIdentifierString(child));
                }
            }
        }

        field.setField(functionCall.toString());
        field.setAlias(extractAlias(aggregateNode));

        if (!currentContext.isEmpty()) {
            field.setSource(currentContext.peek());
        }

        return field;
    }

    private String buildExpressionString(Node exprNode) {
        StringBuilder sb = new StringBuilder();

        if (exprNode.getChildren() != null) {
            for (Node child : exprNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    sb.append(child.getToken().lexeme).append(" ");
                } else {
                    sb.append(buildExpressionString(child)).append(" ");
                }
            }
        }

        return sb.toString().trim();
    }

    private String buildCaseExpressionString(Node caseNode) {
        StringBuilder sb = new StringBuilder("CASE ");

        if (caseNode.getChildren() != null) {
            for (Node child : caseNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    sb.append(child.getToken().lexeme).append(" ");
                } else {
                    sb.append(buildExpressionString(child)).append(" ");
                }
            }
        }

        sb.append("END");
        return sb.toString();
    }

    private String buildAggregateString(Node aggregateNode) {
        StringBuilder sb = new StringBuilder();

        if (aggregateNode.getChildren() != null) {
            for (Node child : aggregateNode.getChildren()) {
                if (child.getNodeType() == NodeType.TERMINAL) {
                    sb.append(child.getToken().lexeme);
                }
            }
        }

        return sb.toString();
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

    private String extractOperandValue(Node operandNode) {
        if (operandNode.getNodeType() == NodeType.TERMINAL) {
            return operandNode.getToken().lexeme;
        } else if (operandNode.getNodeType() == NodeType.IDENTIFIER) {
            return extractIdentifierString(operandNode);
        }
        return null;
    }

    private String extractSubqueryAlias(Node subqueryNode) {
        // ???? AS ? ???????????? ?????????
        return extractAlias(subqueryNode);
    }

    private SubqueryInfo.SubqueryType determineSubqueryTypeFromCondition(String operator, boolean notFlag) {
        if ("EXISTS".equals(operator) || "NOT".equals(operator)) {
            return notFlag ? SubqueryInfo.SubqueryType.NOT_EXISTS : SubqueryInfo.SubqueryType.EXISTS;
        } else if ("IN".equals(operator)) {
            return SubqueryInfo.SubqueryType.IN;
        } else if (operator != null && (operator.equals("=") || operator.equals("!=") ||
                operator.equals("<") || operator.equals(">") ||
                operator.equals("<=") || operator.equals(">="))) {
            return SubqueryInfo.SubqueryType.COMPARISON;
        }
        return SubqueryInfo.SubqueryType.SCALAR;
    }

    private boolean hasCorrelations(Node subqueryNode) {
        if (subqueryNode == null || outerTables.isEmpty()) {
            return false;
        }

        CorrelationAnalyzer analyzer = new CorrelationAnalyzer();
        return analyzer.analyzeForCorrelations(subqueryNode, outerTables, tableAliases);
    }

    private List<CorrelationCondition> extractCorrelationConditions(Node subqueryNode) {
        if (subqueryNode == null) {
            return new ArrayList<>();
        }

        // ???? WHERE ??????? ? ??????????
        Node whereCondition = findWhereCondition(subqueryNode);

        CorrelationAnalyzer analyzer = new CorrelationAnalyzer();
        analyzer.analyzeForCorrelations(subqueryNode, outerTables, tableAliases);

        if (whereCondition != null) {
            return analyzer.extractCorrelationConditions(whereCondition);
        }

        return analyzer.getCorrelations();
    }

    private Node findWhereCondition(Node queryNode) {
        if (queryNode.getChildren() == null) {
            return null;
        }

        for (Node child : queryNode.getChildren()) {
            if (child.getNodeType() == NodeType.LOGICAL_CONDITION) {
                // ????????? ???????? - ??? WHERE ??? HAVING?
                // ??? ???????? ??????? ?????? ?????????? ??????? WHERE
                return child;
            } else if (child.getNodeType() == NodeType.QUERY) {
                // ??????????? ????? ? ???????????
                Node where = findWhereCondition(child);
                if (where != null) {
                    return where;
                }
            }
        }

        return null;
    }

    // ========== ??????????????? ?????? ==========

    private static class TableInfo {
        String tableName;
        String alias;
        boolean isSubquery = false;
    }

    private enum ConditionContext {
        WHERE, HAVING, JOIN, SELECT
    }
}