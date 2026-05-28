package sql.to.mongodb.translator.processors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.IRGenerator;
import sql.to.mongodb.translator.exceptions.IRGenerationException;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinSubquery;
import sql.to.mongodb.translator.ir.join.JoinTable;
import sql.to.mongodb.translator.ir.join.Joinable;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.processors.ExpressionBuilder.extractAlias;
import static sql.to.mongodb.translator.scanner.Category.IDENTIFIER;

@Component
@AllArgsConstructor
public class TableNamesExtractor {

    private final ConditionExtractor conditionExtractor;

    public void processTableNames(IRGenerator irGenerator,
                                  Node tableNamesNode,
                                  IRGenerator.GenerationContext ctx) throws IRGenerationException {
        if (tableNamesNode.getChildren() == null) return;

        JoinInfo currentJoin = new JoinInfo();
        int rightJoinPos = -1;
        int curJoinPos = 0;

        for (Node child : tableNamesNode.getChildren()) {
            switch (child.getNodeType()) {
                case TABLE -> {
                    Joinable operand = extractJoinableFromTable(irGenerator, child, ctx);
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
                    ctx.outerAliases.put(operand.getAlias(), operand instanceof JoinTable t ? t.getValue() : "subquery");
                }
                case TERMINAL, JOIN -> {
                    processJoin(child, currentJoin);
                    ctx.ir.setHasJoins(true);
                }
                case LOGICAL_CONDITION -> {
                    ConditionNode joinCondition = conditionExtractor.extractCondition(
                            child,
                            irGenerator,
                            ctx);
                    currentJoin.setJoinCondition(joinCondition);

                    Joinable newLeft = currentJoin.getRight();

                    rightJoinPos = rightJoinTransformation(currentJoin, curJoinPos, rightJoinPos, ctx);
                    ++curJoinPos;

                    ctx.ir.getJoins().add(currentJoin);
                    currentJoin = new JoinInfo(newLeft);
                }
                case QUERY -> {
                    Joinable operand = processSubqueryAsJoinable(irGenerator, child, ctx);
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
        if (rightJoinPos != -1) {
            rightJoinTransformation(currentJoin, curJoinPos, rightJoinPos, ctx);
        }
    }

    private int rightJoinTransformation(JoinInfo currentJoin,
                                        int curJoinPos,
                                        int rightJoinPos,
                                        IRGenerator.GenerationContext ctx) {
        int newRightJoinPos = rightJoinPos;

        if (currentJoin.getType() != JoinInfo.JoinType.RIGHT) {
            if (rightJoinPos != -1 && curJoinPos != rightJoinPos - 1) {
                if (rightJoinPos == 0) {
                    if (ctx.ir.getJoins().getLast().getRight() instanceof JoinTable) {
                        ctx.ir.setMainCollection(((JoinTable) ctx.ir.getJoins().getLast().getLeft()).getValue());
                    } else {
                        throw new IRGenerationException("RIGHT JOIN с подзапросом в качестве второй таблицы!");
                    }
                }
                int left = rightJoinPos;
                int right = curJoinPos - 1;
                List<JoinInfo> joins = ctx.ir.getJoins();
                List<String> curContext = new ArrayList<>();
                while (left < right) {
                    JoinInfo temp = joins.get(left);
                    joins.set(left, joins.get(right));
                    joins.set(right, temp);
                    left++;
                    right--;
                    for (int i = 0; i < 2; i++) {
                        curContext.add(ctx.currentContext.pop());
                    }
                }
                for (String s : curContext) {
                    ctx.currentContext.push(s);
                }
                newRightJoinPos = -1;
            }
        } else {
            if (rightJoinPos == -1) {
                newRightJoinPos = curJoinPos;
            }
            Joinable left = currentJoin.getLeft();
            Joinable right = currentJoin.getRight();
            currentJoin.setType(JoinInfo.JoinType.LEFT);
            currentJoin.setLeft(right);
            currentJoin.setRight(left);
        }

        return newRightJoinPos;
    }

    private Joinable extractJoinableFromTable(IRGenerator irGenerator,
                                              Node tableNode,
                                              IRGenerator.GenerationContext ctx) throws IRGenerationException {
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
                return processSubqueryAsJoinable(irGenerator, child, ctx);
            }
        }
        JoinTable joinTable = new JoinTable();
        joinTable.setValue(tableName);
        joinTable.setAlias(alias);
        return joinTable;
    }

    private Joinable processSubqueryAsJoinable(IRGenerator irGenerator,
                                               Node subqueryNode,
                                               IRGenerator.GenerationContext ctx) throws IRGenerationException {
        String alias = extractAlias(subqueryNode);
        SqlToMongoIR subqueryIR = irGenerator.generateIR(subqueryNode, ctx.outerTables, ctx.tableAliases);
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
