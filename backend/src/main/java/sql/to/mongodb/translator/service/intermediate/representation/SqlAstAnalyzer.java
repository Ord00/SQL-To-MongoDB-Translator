package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.JoinInfo;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SortField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SqlAstAnalyzer {

    // ???? ??? ???????????? ???????? IR ??? ??????? ???????????
    private Stack<SqlToMongoIR> irStack = new Stack<>();

    public SqlToMongoIR analyze(Node astRoot) {
        if (astRoot == null || astRoot.getNodeType() != NodeType.QUERY) {
            throw new IllegalArgumentException("Invalid AST root node");
        }

        SqlToMongoIR ir = new SqlToMongoIR();
        irStack.push(ir);

        // ???????? ?? ???????? ????? QUERY
        for (Node child : astRoot.getChildren()) {
            analyzeNode(child, ir, null);
        }

        // ?????????? ?????????
        determineAggregationStrategy(ir);

        irStack.pop();
        return ir;
    }

    private void analyzeNode(Node node, SqlToMongoIR currentIR, Node parent) {
        if (node == null) return;

        NodeType nodeType = node.getNodeType();
        Token token = node.getToken();

        switch (nodeType) {
            case TERMINAL:
                analyzeTerminal(node, currentIR, parent);
                break;

            case COLUMN_NAMES:
                analyzeColumnNames(node, currentIR);
                break;

            case TABLE_NAMES:
                analyzeTableNames(node, currentIR);
                break;

            case TABLE:
                analyzeTable(node, currentIR);
                break;

            case JOIN:
                currentIR.setHasJoins(true);
                // JOIN ?????????????? ? TABLE_NAMES
                break;

            case LOGICAL_CONDITION:
                analyzeLogicalCondition(node, currentIR, false); // false = WHERE
                break;

            case LOGICAL_CHECK:
                // ?????????????? ? ???????????? LOGICAL_CONDITION
                break;

            case GROUP_BY:
                analyzeGroupBy(node, currentIR);
                break;

            case ORDER_BY:
                analyzeOrderBy(node, currentIR);
                break;

            case AGGREGATE:
                analyzeAggregate(node, currentIR);
                break;

            case QUERY:
                // ????????? - ???????????? ??????????
                handleSubquery(node, currentIR, parent);
                break;

            case IDENTIFIER:
            case ARITHMETIC_EXP:
            case CASE:
            case CASE_PART:
                // ?????????????? ? ???????????? ?????
                break;

            default:
                // ?????????? ??????? ???????? ????
                for (Node child : node.getChildren()) {
                    analyzeNode(child, currentIR, node);
                }
                break;
        }
    }

    private void handleSubquery(Node subqueryNode, SqlToMongoIR parentIR, Node parent) {
        // ??????? ????? IR ??? ??????????
        SqlToMongoIR subqueryIR = new SqlToMongoIR();
        irStack.push(subqueryIR);

        // ??????????? ?????????
        for (Node child : subqueryNode.getChildren()) {
            analyzeNode(child, subqueryIR, subqueryNode);
        }

        // ??????? SubqueryInfo
        SubqueryInfo subqueryInfo = new SubqueryInfo();
        subqueryInfo.setSubqueryIR(subqueryIR);

        // ?????????? ??? ?????????? ?? ?????????
        SubqueryInfo.SubqueryType type = determineSubqueryType(parent);
        subqueryInfo.setType(type);

        // ????????? ? ???????????? IR
        parentIR.getSubqueries().add(subqueryInfo);
        parentIR.setHasSubqueries(true);

        // ????????? ?? ??????????
        if (hasCorrelation(subqueryNode, parentIR)) {
            parentIR.setHasCorrelatedSubqueries(true);
        }

        irStack.pop();
    }

    private SubqueryInfo.SubqueryType determineSubqueryType(Node parent) {
        if (parent == null) return SubqueryInfo.SubqueryType.SCALAR;

        // ???? ? ???????????? ???? ????????? ??? ??????????? ????
        List<Node> terminals = findNodesByType(parent, NodeType.TERMINAL);
        for (Node terminal : terminals) {
            Token token = terminal.getToken();
            if (token != null) {
                if (token.category == Category.LOGICAL_EXPRESSION) {
                    if ("EXISTS".equals(token.lexeme)) {
                        return SubqueryInfo.SubqueryType.EXISTS;
                    } else if ("NOT".equals(token.lexeme)) {
                        // ????????? ????????? ?????
                        return SubqueryInfo.SubqueryType.NOT_EXISTS;
                    }
                } else if (token.category == Category.LOGICAL_OPERATOR) {
                    if ("IN".equals(token.lexeme)) {
                        return SubqueryInfo.SubqueryType.IN;
                    }
                }
            }
        }

        return SubqueryInfo.SubqueryType.SCALAR;
    }

    private boolean hasCorrelation(Node subqueryNode, SqlToMongoIR parentIR) {
        // ??????? ????????: ???? ? ?????????? ???? ?????? ?? ??????? ?? ????????????? ???????
        List<Node> identifiers = findNodesByType(subqueryNode, NodeType.IDENTIFIER);
        for (Node identifier : identifiers) {
            Token token = identifier.getToken();
            if (token != null) {
                String fieldName = token.lexeme;
                // ?????????, ???????? ?? ??? ??????? ?? ??????? ?? ????????????? IR
                if (parentIR.getAliases().containsKey(fieldName) ||
                        fieldName.equals(parentIR.getMainCollection())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void analyzeTerminal(Node terminalNode, SqlToMongoIR ir, Node parent) {
        Token token = terminalNode.getToken();
        if (token == null) return;

        Category category = token.category;
        String lexeme = token.lexeme;

        switch (category) {
            case DML:
                // ??? ?????????? ?? ??????? ??????
                break;

            case KEYWORD:
                handleKeyword(lexeme, ir, parent);
                break;

            case AGGREGATE:
                // ?????????? ??????? ?????????????? ? AGGREGATE ????
                break;

            case LOGICAL_EXPRESSION:
                if ("EXISTS".equals(lexeme) || "NOT".equals(lexeme)) {
                    ir.setHasExistsConditions(true);
                    ir.setHasSubqueries(true);
                }
                break;

            case IDENTIFIER:
            case LITERAL:
            case NUMBER:
                // ?????????????? ? ???????????? ?????
                break;

            default:
                // ?????????? ?????? ?????????
                break;
        }
    }

    private void handleKeyword(String keyword, SqlToMongoIR ir, Node parent) {
        switch (keyword.toUpperCase()) {
            case "DISTINCT":
                ir.setDistinct(true);
                break;

            case "AS":
                // ?????? ?????????????? ? analyzeColumnNames ? analyzeTable
                break;

            case "JOIN":
                ir.setHasJoins(true);
                break;

            case "WHERE":
            case "HAVING":
            case "GROUP":
            case "ORDER":
            case "BY":
                // ?????????????? ? ??????????????? ?????
                break;

            case "LIMIT":
                if (parent != null) {
                    analyzeLimit(parent, ir);
                }
                break;
        }
    }

    private void analyzeColumnNames(Node columnNamesNode, SqlToMongoIR ir) {
        for (Node columnNode : columnNamesNode.getChildren()) {
            ProjectionField field = extractProjectionField(columnNode, ir);
            if (field != null) {
                ir.getProjectionFields().add(field);

                // ????????? ?? ??????? ?????????
                if (!field.isAllFields() && containsArithmeticExp(columnNode)) {
                    ir.setHasComplexProjections(true);
                }
            }
        }
    }

    private ProjectionField extractProjectionField(Node node, SqlToMongoIR ir) {
        // ???? ?????????: IDENTIFIER (. IDENTIFIER)? ??? AGGREGATE
        List<Node> identifiers = findNodesByType(node, NodeType.IDENTIFIER);
        List<Node> aggregates = findNodesByType(node, NodeType.AGGREGATE);

        ProjectionField field = new ProjectionField();

        if (!aggregates.isEmpty()) {
            // ?????????? ???????
            ir.setHasAggregateFunctions(true);
            field.setField(extractAggregateExpression(aggregates.getFirst()));
            return field;
        }

        if (!identifiers.isEmpty()) {
            if (identifiers.size() == 1) {
                // ??????? ????: column_name
                field.setField(identifiers.getFirst().getToken().lexeme);
            } else if (identifiers.size() >= 2) {
                // ????????????????? ????: table.column ??? alias.*
                String firstToken = identifiers.get(0).getToken().lexeme;
                String secondToken = identifiers.get(1).getToken().lexeme;

                // ?????????, ???????? ?? ?????? ????? "ALL" (*)
                if ("*".equals(firstToken)) {
                    field.setField("*");
                    if (identifiers.size() > 1) {
                        field.setSource(secondToken);
                    }
                } else {
                    field.setSource(firstToken);
                    field.setField(secondToken);
                }
            }

            // ????????? ?? ?????
            for (int i = 0; i < identifiers.size(); i++) {
                Node idNode = identifiers.get(i);
                if (idNode.getToken().category == Category.KEYWORD &&
                        "AS".equals(idNode.getToken().lexeme)) {
                    if (i + 1 < identifiers.size()) {
                        field.setAlias(identifiers.get(i + 1).getToken().lexeme);
                    }
                }
            }
        }

        return field;
    }

    private void analyzeTableNames(Node tableNamesNode, SqlToMongoIR ir) {
        boolean isFirstTable = true;
        JoinInfo currentJoin = null;

        for (Node child : tableNamesNode.getChildren()) {
            if (child.getNodeType() == NodeType.TABLE) {
                TableInfo tableInfo = extractTableInfo(child);

                if (isFirstTable) {
                    ir.setMainCollection(tableInfo.tableName);
                    if (tableInfo.alias != null) {
                        ir.getAliases().put(tableInfo.alias, tableInfo.tableName);
                    }
                    isFirstTable = false;

                    // ??? ?????? ??????? ????????????? leftTable ? JOIN
                    if (currentJoin != null) {
                        currentJoin.setLeftTable(tableInfo.tableName);
                        currentJoin.setLeftAlias(tableInfo.alias);
                    }
                } else {
                    // ??? JOIN ???????
                    if (currentJoin == null) {
                        currentJoin = new JoinInfo();
                        // ????????????? ?????????? ??????? ??? left
                        // ????? ??????? ?????????? ???????
                    }
                    currentJoin.setRightTable(tableInfo.tableName);
                    currentJoin.setRightAlias(tableInfo.alias);

                    if (tableInfo.alias != null) {
                        ir.getAliases().put(tableInfo.alias, tableInfo.tableName);
                    }
                }
            } else if (child.getNodeType() == NodeType.JOIN) {
                // ???????? ????? JOIN
                if (currentJoin != null) {
                    ir.getJoins().add(currentJoin);
                }
                currentJoin = new JoinInfo();
                currentJoin.setType(JoinInfo.JoinType.INNER);
            } else if (child.getNodeType() == NodeType.LOGICAL_CONDITION) {
                // ??????? JOIN
                if (currentJoin != null) {
                    currentJoin.setJoinCondition(extractJoinCondition(child));
                }
            }
        }

        // ????????? ????????? JOIN
        if (currentJoin != null) {
            ir.getJoins().add(currentJoin);
        }
    }

    private void analyzeTable(Node tableNode, SqlToMongoIR ir) {
        // ???? ????? ?????????? ??? ????????? TABLE ?????
        // ???????? ?????? ??? ? analyzeTableNames
        TableInfo tableInfo = extractTableInfo(tableNode);

        // ????? ???????? ?????????????? ????????? ???? ?????
    }

    private TableInfo extractTableInfo(Node tableNode) {
        TableInfo info = new TableInfo();
        List<Node> identifiers = findNodesByType(tableNode, NodeType.IDENTIFIER);

        if (!identifiers.isEmpty()) {
            // ?????? ????????????? - ??? ???????
            info.tableName = identifiers.getFirst().getToken().lexeme;

            // ???? ????? (????? KEYWORD "AS" ??? ????? ????? ?????)
            for (int i = 1; i < identifiers.size(); i++) {
                Node idNode = identifiers.get(i);
                Token token = idNode.getToken();

                if (token.category == Category.KEYWORD && "AS".equals(token.lexeme)) {
                    if (i + 1 < identifiers.size()) {
                        info.alias = identifiers.get(i + 1).getToken().lexeme;
                        break;
                    }
                } else if (info.alias == null) {
                    // ????? ??? AS (table_name alias)
                    info.alias = token.lexeme;
                }
            }
        }

        return info;
    }

    private void analyzeLogicalCondition(Node conditionNode, SqlToMongoIR ir, boolean isHaving) {
        ConditionNode condition = buildConditionTree(conditionNode, ir);

        if (isHaving) {
            ir.getHavingConditions().add(condition);
            ir.setHasHaving(true);

            // ????????? ?????????? ??????? ? HAVING
            if (containsAggregate(conditionNode)) {
                ir.setHasAggregateFunctions(true);
            }
        } else {
            ir.getWhereConditions().add(condition);

            // ????????? ??????????
            if (containsSubquery(conditionNode)) {
                ir.setHasSubqueries(true);

                // ????????? ??????????
                if (isCorrelatedSubquery(conditionNode, ir)) {
                    ir.setHasCorrelatedSubqueries(true);
                }
            }
        }
    }

    private ConditionNode buildConditionTree(Node node, SqlToMongoIR currentIR) {
        ConditionNode condition = new ConditionNode();

        // ???? ?????????? ????????? ? ?????????
        List<Node> terminals = findNodesByType(node, NodeType.TERMINAL);
        for (Node terminal : terminals) {
            Token token = terminal.getToken();
            if (token == null) continue;

            switch (token.category) {
                case LOGICAL_EXPRESSION:
                    if ("EXISTS".equals(token.lexeme)) {
                        condition.setType(ConditionNode.ConditionType.EXISTS);
                    } else if ("IS".equals(token.lexeme)) {
                        condition.setType(ConditionNode.ConditionType.IS_NULL);
                    }
                    break;

                case LOGICAL_OPERATOR:
                    condition.setOperator(token.lexeme);
                    break;

                case LOGICAL_COMBINE:
                    if ("AND".equals(token.lexeme)) {
                        condition.setType(ConditionNode.ConditionType.AND);
                    } else if ("OR".equals(token.lexeme)) {
                        condition.setType(ConditionNode.ConditionType.OR);
                    }
                    break;
            }
        }

        // ?????????? ???????????? ???????? ???????
        for (Node child : node.getChildren()) {
            if (child.getNodeType() == NodeType.LOGICAL_CHECK ||
                    child.getNodeType() == NodeType.LOGICAL_CONDITION) {
                condition.getChildren().add(buildConditionTree(child, currentIR));
            } else if (child.getNodeType() == NodeType.QUERY) {
                // ????????? - ???????????? ????????
                handleSubquery(child, currentIR, node);
            }
        }

        return condition;
    }

    private void analyzeGroupBy(Node groupByNode, SqlToMongoIR ir) {
        ir.setHasGroupBy(true);

        for (Node child : groupByNode.getChildren()) {
            List<Node> identifiers = findNodesByType(child, NodeType.IDENTIFIER);
            if (!identifiers.isEmpty()) {
                // ????? ????????? ????????????? ??? ??? ????
                Token fieldToken = identifiers.getLast().getToken();
                ir.getGroupByFields().add(fieldToken.lexeme);
            }
        }
    }

    private void analyzeOrderBy(Node orderByNode, SqlToMongoIR ir) {
        for (Node child : orderByNode.getChildren()) {
            SortField sortField = extractSortField(child);
            if (sortField != null) {
                ir.getOrderBy().add(sortField);
            }
        }
    }

    private SortField extractSortField(Node node) {
        SortField field = new SortField();

        List<Node> identifiers = findNodesByType(node, NodeType.IDENTIFIER);
        if (!identifiers.isEmpty()) {
            // ????? ????????? ????????????? ??? ????
            field.setField(identifiers.getLast().getToken().lexeme);

            // ???? ???? ???????? (table.field)
            if (identifiers.size() > 1) {
                field.setSource(identifiers.getFirst().getToken().lexeme);
            }
        }

        // ???? ???????????
        List<Node> terminals = findNodesByType(node, NodeType.TERMINAL);
        for (Node terminal : terminals) {
            Token token = terminal.getToken();
            if (token != null && token.category == Category.KEYWORD) {
                if ("ASC".equals(token.lexeme)) {
                    field.setDirection(SortField.SortDirection.ASC);
                } else if ("DESC".equals(token.lexeme)) {
                    field.setDirection(SortField.SortDirection.DESC);
                }
            }
        }

        return field;
    }

    private void analyzeAggregate(Node aggregateNode, SqlToMongoIR ir) {
        ir.setHasAggregateFunctions(true);

        // ????????? DISTINCT ?????? ?????????? ???????
        List<Node> terminals = findNodesByType(aggregateNode, NodeType.TERMINAL);
        for (Node terminal : terminals) {
            Token token = terminal.getToken();
            if (token != null && token.category == Category.KEYWORD &&
                    "DISTINCT".equals(token.lexeme)) {
                ir.setDistinct(true);
            }
        }
    }

    private void analyzeLimit(Node parentNode, SqlToMongoIR ir) {
        // ???? NUMBER ????? LIMIT
        for (Node child : parentNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                if (token != null && token.category == Category.NUMBER) {
                    try {
                        ir.setLimit(Integer.parseInt(token.lexeme));
                    } catch (NumberFormatException e) {
                        // ??????????
                    }
                }
            }
        }
    }

    private void determineAggregationStrategy(SqlToMongoIR ir) {
        // ??????????, ????? ?? ?????????
        boolean needsAggregation =
                ir.isDistinct() ||
                        !ir.getJoins().isEmpty() ||
                        ir.isHasGroupBy() ||
                        ir.isHasHaving() ||
                        ir.isHasAggregateFunctions() ||
                        ir.isHasExistsConditions() ||
                        ir.isHasCorrelatedSubqueries() ||
                        ir.isHasWindowFunctions() ||
                        ir.isHasComplexProjections();

        ir.setRequiresAggregation(needsAggregation);
    }

    // ??????????????? ??????
    private List<Node> findNodesByType(Node root, NodeType type) {
        List<Node> result = new ArrayList<>();
        findNodesByTypeRecursive(root, type, result);
        return result;
    }

    private void findNodesByTypeRecursive(Node node, NodeType type, List<Node> result) {
        if (node == null) return;

        if (node.getNodeType() == type) {
            result.add(node);
        }

        for (Node child : node.getChildren()) {
            findNodesByTypeRecursive(child, type, result);
        }
    }

    private boolean containsArithmeticExp(Node node) {
        return !findNodesByType(node, NodeType.ARITHMETIC_EXP).isEmpty();
    }

    private boolean containsAggregate(Node node) {
        return !findNodesByType(node, NodeType.AGGREGATE).isEmpty();
    }

    private boolean containsSubquery(Node node) {
        return !findNodesByType(node, NodeType.QUERY).isEmpty();
    }

    private boolean isCorrelatedSubquery(Node node, SqlToMongoIR ir) {
        List<Node> subqueries = findNodesByType(node, NodeType.QUERY);
        // TODO: ??????????? ???????? ??????????
        return !subqueries.isEmpty();
    }

    private String extractAggregateExpression(Node aggregateNode) {
        StringBuilder expr = new StringBuilder();

        // ???? ??? ????????? ? ?????????? ???????
        List<Node> terminals = findNodesByType(aggregateNode, NodeType.TERMINAL);
        for (Node terminal : terminals) {
            Token token = terminal.getToken();
            if (token != null) {
                expr.append(token.lexeme).append(" ");
            }
        }

        // ???? ?????????????? (???? ??? ?????????)
        List<Node> identifiers = findNodesByType(aggregateNode, NodeType.IDENTIFIER);
        if (!identifiers.isEmpty()) {
            expr.append(identifiers.getLast().getToken().lexeme);
        }

        return expr.toString().trim();
    }

    private ConditionNode extractJoinCondition(Node conditionNode) {
        // ?????????? ??????????
        ConditionNode condition = new ConditionNode();
        condition.setType(ConditionNode.ConditionType.SIMPLE);

        // ???? ???????? ?????????
        List<Node> terminals = findNodesByType(conditionNode, NodeType.TERMINAL);
        for (Node terminal : terminals) {
            Token token = terminal.getToken();
            if (token != null && token.category == Category.LOGICAL_OPERATOR) {
                condition.setOperator(token.lexeme);
                break;
            }
        }

        return condition;
    }

    // ??????????????? ????? ??? ?????????? ? ???????
    private static class TableInfo {
        String tableName;
        String alias;
    }
}
