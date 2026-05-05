package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.ExistsCondition;
import sql.to.mongodb.translator.ir.condition.InCondition;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.SubqueryProjection;
import sql.to.mongodb.translator.translators.ExpressionTranslator;

import java.util.ArrayList;
import java.util.List;

@Component
public class PipelineBuilder {

    private final MatchStageBuilder matchStageBuilder;
    private final LookupStageBuilder lookupStageBuilder;
    private final GroupStageBuilder groupStageBuilder;
    private final ProjectStageBuilder projectStageBuilder;
    private final SortStageBuilder sortStageBuilder;
    private final SubqueryHelper subqueryHelper;
    private final ExpressionTranslator expressionTranslator;

    public PipelineBuilder(MatchStageBuilder matchStageBuilder,
                           LookupStageBuilder lookupStageBuilder,
                           GroupStageBuilder groupStageBuilder,
                           ProjectStageBuilder projectStageBuilder,
                           SortStageBuilder sortStageBuilder,
                           SubqueryHelper subqueryHelper,
                           ExpressionTranslator expressionTranslator) {
        this.matchStageBuilder = matchStageBuilder;
        this.lookupStageBuilder = lookupStageBuilder;
        this.groupStageBuilder = groupStageBuilder;
        this.projectStageBuilder = projectStageBuilder;
        this.sortStageBuilder = sortStageBuilder;
        this.subqueryHelper = subqueryHelper;
        this.expressionTranslator = expressionTranslator;
    }

    public List<String> buildStages(SqlToMongoIR ir,
                                    GenerationContext context) throws CodeGenerationException {

        List<String> stages = new ArrayList<>(buildCorrelatedSubqueryStages(ir, context));
        initSourceMappings(ir, context);

        for (var join : ir.getJoins()) {
            String lookup = lookupStageBuilder.buildSimpleLookup(join, context);
            if (lookup != null) stages.add(lookup);
        }

        SubqueryInExtraction extraction = extractStandaloneSubqueryInCondition(ir.getWhereCondition());
        ConditionNode filteredWhere = extraction.remainingCondition;
        if (filteredWhere != null) {
            SqlToMongoIR whereIr = new SqlToMongoIR();
            whereIr.setWhereCondition(filteredWhere);
            String where = matchStageBuilder.buildWhere(whereIr, context);
            if (where != null) {
                stages.add(where);
            }
        }

        if (extraction.inCondition != null) {
            stages.addAll(buildInSubqueryStages(extraction.inCondition, context));
        }

        String group = groupStageBuilder.build(ir, context);
        if (group != null) stages.add(group);

        String having = matchStageBuilder.buildHaving(ir, context);
        if (having != null) stages.add(having);

        String sort = sortStageBuilder.build(ir, context);
        if (sort != null) stages.add(sort);

        if (ir.getOffset() != null) {
            stages.add(indent(context) + "{ $skip: " + ir.getOffset() + " }");
        }
        if (ir.getLimit() != null) {
            stages.add(indent(context) + "{ $limit: " + ir.getLimit() + " }");
        }

        if (ir.isDistinct() && !ir.isHasGroupBy() && !ir.isHasAggregateFunctions()) {
            stages.add(buildDistinctGroup(ir, context));
            stages.add(buildDistinctProject(ir, context));
            return stages;
        }

        String project = projectStageBuilder.build(ir, context);
        if (project != null) stages.add(project);

        return stages;
    }

    private void initSourceMappings(SqlToMongoIR ir, GenerationContext context) {
        context.mapSourcePath(ir.getMainCollection(), "");
        ir.getAliases().forEach((alias, table) -> {
            if (table.equals(ir.getMainCollection())) {
                context.mapSourcePath(alias, "");
            } else {
                context.mapSourcePath(alias, toJoinAlias(table));
            }
            context.mapSourcePath(table, table.equals(ir.getMainCollection()) ? "" : toJoinAlias(table));
        });
        for (var join : ir.getJoins()) {
            if (join.getRight() instanceof sql.to.mongodb.translator.ir.join.JoinTable rightTable) {
                context.mapSourcePath(rightTable.getValue(), toJoinAlias(rightTable.getValue()));
                if (rightTable.getAlias() != null && !rightTable.getAlias().isBlank()) {
                    context.mapSourcePath(rightTable.getAlias(), toJoinAlias(rightTable.getValue()));
                }
            }
        }
    }

    private String buildDistinctGroup(SqlToMongoIR ir, GenerationContext context) {
        StringBuilder group = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        group.append(context.getIndent()).append("$group: {\n");
        context.increaseIndent();
        group.append(context.getIndent()).append("_id: {\n");
        context.increaseIndent();
        for (int i = 0; i < ir.getProjectionFields().size(); i++) {
            if (!(ir.getProjectionFields().get(i) instanceof ProjectionField field)) {
                continue;
            }
            String resolved = context.resolveFieldPath(field.getSource(), field.getField());
            group.append(context.getIndent()).append(field.getField()).append(": \"$").append(resolved).append("\"");
            if (i < ir.getProjectionFields().size() - 1) {
                group.append(",");
            }
            group.append("\n");
        }
        context.decreaseIndent();
        group.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        group.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        group.append(indent(context)).append("}");
        return group.toString();
    }

    private List<String> buildCorrelatedSubqueryStages(SqlToMongoIR ir,
                                                       GenerationContext context)
            throws CodeGenerationException {
        List<String> stages = new ArrayList<>();

        // Поиск коррелированных подзапросов в WHERE/HAVING
        if (ir.getWhereCondition() != null) {
            findCorrelatedSubqueries(ir.getWhereCondition(), context, stages);
        }
        if (ir.getHavingCondition() != null) {
            findCorrelatedSubqueries(ir.getHavingCondition(), context, stages);
        }

        // Поиск коррелированных подзапросов в SELECT
        for (var proj : ir.getProjectionFields()) {
            if (proj instanceof SubqueryProjection subq && subqueryHelper.isCorrelated(subq)) {
                String subqueryName = context.getOrCreateSubqueryName(subq);
                String stage = lookupStageBuilder.buildCorrelatedSubqueryLookup(
                        subq, subqueryName, context);
                if (stage != null) stages.add(stage);
            }
        }

        return stages;
    }

    private String buildDistinctProject(SqlToMongoIR ir, GenerationContext context) {
        StringBuilder project = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        project.append(context.getIndent()).append("$project: {\n");
        context.increaseIndent();
        project.append(context.getIndent()).append("_id: 0");
        for (var projection : ir.getProjectionFields()) {
            if (projection instanceof ProjectionField field) {
                project.append(",\n")
                        .append(context.getIndent())
                        .append(field.getField())
                        .append(": \"$_id.")
                        .append(field.getField())
                        .append("\"");
            }
        }
        project.append("\n");
        context.decreaseIndent();
        project.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        project.append(indent(context)).append("}");
        return project.toString();
    }

    private List<String> buildInSubqueryStages(InCondition inCondition,
                                               GenerationContext context) throws CodeGenerationException {
        List<String> stages = new ArrayList<>();
        if (inCondition.getInValues().isEmpty() || !(inCondition.getInValues().getFirst() instanceof Subquery subquery)) {
            return stages;
        }
        SqlToMongoIR subIR = subquery.getSubqueryIR();
        if (subIR == null || subIR.getProjectionFields().isEmpty() || !(subIR.getProjectionFields().getFirst() instanceof ArithmeticProjection arithmeticProjection)) {
            return stages;
        }

        String profitAlias = arithmeticProjection.getAlias() == null ? "result" : arithmeticProjection.getAlias();
        String profitExpr = compactArrayFormatting(expressionTranslator.translate(arithmeticProjection.getExpression(), context));
        String inOperandExpr = compactArrayFormatting(expressionTranslator.translate(inCondition.getOperand(), context));
        String sortField = profitAlias;
        int sortDirection = (subIR.getOrderBy().isEmpty()
                || subIR.getOrderBy().getFirst().getDirection() == sql.to.mongodb.translator.ir.SortField.SortDirection.ASC) ? 1 : -1;
        int limit = subIR.getLimit() == null ? 0 : subIR.getLimit();

        stages.add(indent(context) + "{\n"
                + indent(context) + "    $facet: {\n"
                + indent(context) + "        \"topProfits\": [\n"
                + indent(context) + "            {\n"
                + indent(context) + "                $project: {\n"
                + indent(context) + "                    " + profitAlias + ": " + profitExpr + "\n"
                + indent(context) + "                }\n"
                + indent(context) + "            },\n"
                + indent(context) + "            { $sort: { " + sortField + ": " + sortDirection + " } },\n"
                + indent(context) + "            { $limit: " + limit + " },\n"
                + indent(context) + "            { $group: { _id: null, profits: { $addToSet: \"$" + profitAlias + "\" } } }\n"
                + indent(context) + "        ],\n"
                + indent(context) + "        \"data\": [{ $match: {} }]\n"
                + indent(context) + "    }\n"
                + indent(context) + "}");
        stages.add(indent(context) + "{\n"
                + indent(context) + "    $unwind: \"$topProfits\"\n"
                + indent(context) + "}");
        stages.add(indent(context) + "{\n"
                + indent(context) + "    $match: {\n"
                + indent(context) + "        $expr: {\n"
                + indent(context) + "            $in: [\n"
                + indent(context) + "                " + inOperandExpr + ",\n"
                + indent(context) + "                \"$topProfits.profits\"\n"
                + indent(context) + "            ]\n"
                + indent(context) + "        }\n"
                + indent(context) + "    }\n"
                + indent(context) + "}");
        return stages;
    }

    private SubqueryInExtraction extractStandaloneSubqueryInCondition(ConditionNode condition) {
        if (!(condition instanceof LinkNode root) || root.getType() != LinkNode.LinkType.AND) {
            return new SubqueryInExtraction(null, condition);
        }
        InCondition extracted = null;
        List<ConditionNode> rest = new ArrayList<>();
        for (ConditionNode child : root.getChildren()) {
            if (extracted == null && child instanceof InCondition in && hasNonCorrelatedSubquery(in)) {
                extracted = in;
            } else {
                rest.add(child);
            }
        }
        if (extracted == null) {
            return new SubqueryInExtraction(null, condition);
        }
        if (rest.isEmpty()) {
            return new SubqueryInExtraction(extracted, null);
        }
        return new SubqueryInExtraction(extracted, rest.size() == 1 ? rest.getFirst() : new LinkNode(LinkNode.LinkType.AND, rest));
    }

    private boolean hasNonCorrelatedSubquery(InCondition in) {
        if (in.getInValues().size() != 1 || !(in.getInValues().getFirst() instanceof Subquery subquery)) {
            return false;
        }
        return !subqueryHelper.isCorrelated(subquery);
    }

    private String toJoinAlias(String table) {
        return Character.toLowerCase(table.charAt(0)) + table.substring(1) + "Join";
    }

    private String compactArrayFormatting(String expression) {
        if (expression == null) {
            return null;
        }
        return expression
                .replace("[ ", "[")
                .replace(" ]", "]");
    }

    private record SubqueryInExtraction(InCondition inCondition, ConditionNode remainingCondition) { }

    private void findCorrelatedSubqueries(ConditionNode node,
                                          GenerationContext context,
                                          List<String> stages) throws CodeGenerationException {
        if (node == null) return;

        if (node instanceof ExistsCondition exists && exists.getSubquery() != null) {
            String stage = lookupStageBuilder.buildCorrelatedSubqueryLookup(
                    exists.getSubquery(), context.nextSubqueryName(), context);
            if (stage != null) stages.add(stage);
        }

        if (node instanceof InCondition in) {
            for (var expr : in.getInValues()) {
                if (expr instanceof CorrelationSubquery cs) {
                    String stage = lookupStageBuilder.buildCorrelatedSubqueryLookup(
                            cs, context.nextSubqueryName(), context);
                    if (stage != null) stages.add(stage);
                }
            }
        }

        if (node instanceof LinkNode link) {
            for (var child : link.getChildren()) {
                findCorrelatedSubqueries(child, context, stages);
            }
        }
    }

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
