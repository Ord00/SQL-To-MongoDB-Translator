package sql.to.mongodb.translator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.IRGenerationException;
import sql.to.mongodb.translator.ir.GroupByField;
import sql.to.mongodb.translator.ir.SortField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.processors.ColumnNamesExtractor;
import sql.to.mongodb.translator.processors.ConditionExtractor;
import sql.to.mongodb.translator.processors.TableNamesExtractor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import static sql.to.mongodb.translator.scanner.Category.IDENTIFIER;

@Component
@AllArgsConstructor
public class IRGenerator {

    private final ColumnNamesExtractor columnNamesExtractor;
    private final TableNamesExtractor tableNamesExtractor;
    private final ConditionExtractor conditionExtractor;

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

    public static class GenerationContext {
        public final SqlToMongoIR ir;
        public final Map<String, String> tableAliases;
        public final Stack<String> currentContext;
        public final Set<String> outerTables;
        public final Map<String, String> outerAliases;
        @Getter
        @Setter
        public ConditionExtractor.ConditionContext conditionContext;

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
        }
    }

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

    private void processTerminalInQuery(Node terminalNode, GenerationContext ctx) {
        switch (terminalNode.getToken().lexeme) {
            case "DISTINCT" -> ctx.ir.setDistinct(true);
            case "WHERE" -> ctx.setConditionContext(ConditionExtractor.ConditionContext.WHERE);
            case "HAVING" -> ctx.setConditionContext(ConditionExtractor.ConditionContext.HAVING);
        }
    }

    private void processConditionNode(Node conditionNode, GenerationContext ctx) {
        ConditionNode extractedCondition = conditionExtractor.extractCondition(
                conditionNode,
                this,
                ctx);

        if (extractedCondition != null) {
            switch (ctx.getConditionContext()) {
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

    private void processColumnNames(Node columnNamesNode,
                                    GenerationContext ctx) throws IRGenerationException {
        columnNamesExtractor.processColumnNames(this, columnNamesNode, ctx);
    }

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

    private void processLimit(Node limitNode, GenerationContext ctx) {
        if (limitNode.getChildren() == null) return;
        ctx.ir.setLimit(Integer.parseInt(limitNode.getChildren().getFirst().getToken().lexeme));
    }

    private void processOffset(Node offsetNode, GenerationContext ctx) {
        if (offsetNode.getChildren() == null) return;
        ctx.ir.setOffset(Integer.parseInt(offsetNode.getChildren().getFirst().getToken().lexeme));
    }

    private void processTableNames(Node tableNamesNode,
                                   GenerationContext ctx) throws IRGenerationException {
        tableNamesExtractor.processTableNames(this, tableNamesNode, ctx);
    }
}