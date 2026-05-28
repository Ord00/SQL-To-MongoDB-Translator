package sql.to.mongodb.translator.processors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.IRGenerator;
import sql.to.mongodb.translator.exceptions.IRGenerationException;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;
import sql.to.mongodb.translator.ir.expression.Arithmetical;
import sql.to.mongodb.translator.ir.expression.CaseExpression;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.CaseProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.SubqueryProjection;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;

import java.util.List;

import static sql.to.mongodb.translator.processors.ExpressionBuilder.extractAlias;
import static sql.to.mongodb.translator.processors.ExpressionBuilder.extractFieldParts;
import static sql.to.mongodb.translator.processors.ExpressionBuilder.processAlias;

@Component
@AllArgsConstructor
public class ColumnNamesExtractor {

    private final ConditionExtractor conditionExtractor;

    public void processColumnNames(IRGenerator irGenerator,
                                   Node columnNamesNode,
                                   IRGenerator.GenerationContext ctx) throws IRGenerationException {
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
                case QUERY -> i = processSubqueryInProjection(irGenerator, columns, i, ctx);
                default -> {
                    ProjectionField defaultField = new ProjectionField();
                    defaultField.setField(ExpressionBuilder.buildExpressionString(columns.get(i)));
                    i = processAlias(columns, i, defaultField);
                    ctx.ir.getProjectionFields().add(defaultField);
                }
            }
        }
    }

    private void processAllColumns(IRGenerator.GenerationContext ctx) {
        ProjectionField field = new ProjectionField();
        field.setField("*");
        if (!ctx.currentContext.isEmpty()) {
            field.setSource(ctx.currentContext.peek());
        }
        ctx.ir.getProjectionFields().add(field);
    }

    private int processSubqueryInProjection(IRGenerator irGenerator,
                                            List<Node> columns,
                                            int i,
                                            IRGenerator.GenerationContext ctx) throws IRGenerationException {
        int result = i;

        SqlToMongoIR subqueryIR = irGenerator.generateIR(columns.get(i), ctx.outerTables, ctx.tableAliases);

        SubqueryProjection projection = new SubqueryProjection();
        projection.setSubqueryIR(subqueryIR);
        ++result;

        String alias = extractAlias(columns, result);
        if (alias != null) {
            result += 2;
            projection.setAlias(alias);
        }

        List<CorrelationCondition> correlations = conditionExtractor.extractCorrelations(columns.get(i), ctx);
        projection.getCorrelations().addAll(correlations);

        ctx.ir.getProjectionFields().add(projection);
        ctx.ir.setHasSubqueries(true);
        ctx.ir.setHasComplexProjections(true);

        return result;
    }

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
}
