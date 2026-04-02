package sql.to.mongodb.translator.service.code.generator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.model.CorrelationSubquery;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.ExistsCondition;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.InCondition;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.LinkNode;
import sql.to.mongodb.translator.service.intermediate.representation.model.projection.SubqueryProjection;

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

    public PipelineBuilder(MatchStageBuilder matchStageBuilder,
                           LookupStageBuilder lookupStageBuilder,
                           GroupStageBuilder groupStageBuilder,
                           ProjectStageBuilder projectStageBuilder,
                           SortStageBuilder sortStageBuilder,
                           SubqueryHelper subqueryHelper) {
        this.matchStageBuilder = matchStageBuilder;
        this.lookupStageBuilder = lookupStageBuilder;
        this.groupStageBuilder = groupStageBuilder;
        this.projectStageBuilder = projectStageBuilder;
        this.sortStageBuilder = sortStageBuilder;
        this.subqueryHelper = subqueryHelper;
    }

    public List<String> buildStages(SqlToMongoIR ir,
                                    GenerationContext context) throws CodeGenerationException {

        // Коррелированные подзапросы (требуют $lookup)
        List<String> stages = new ArrayList<>(buildCorrelatedSubqueryStages(ir, context));

        // $match (WHERE)
        String where = matchStageBuilder.buildWhere(ir, context);
        if (where != null) stages.add(where);

        // $lookup (JOINs)
        for (var join : ir.getJoins()) {
            String lookup = lookupStageBuilder.buildSimpleLookup(join, context);
            if (lookup != null) stages.add(lookup);
        }

        // $match для условий JOIN (после $lookup)
        List<ConditionNode> joinConditions = extractJoinConditions(ir);
        if (!joinConditions.isEmpty()) {
            String joinMatch = matchStageBuilder.buildJoinMatch(joinConditions, context);
            if (joinMatch != null) stages.add(joinMatch);
        }

        // $group (GROUP BY + агрегации)
        String group = groupStageBuilder.build(ir, context);
        if (group != null) stages.add(group);

        // $match (HAVING)
        String having = matchStageBuilder.buildHaving(ir, context);
        if (having != null) stages.add(having);

        // $sort
        String sort = sortStageBuilder.build(ir, context);
        if (sort != null) stages.add(sort);

        // $skip/$limit
        if (ir.getOffset() != null) {
            stages.add(indent(context) + "{ $skip: " + ir.getOffset() + " }");
        }
        if (ir.getLimit() != null) {
            stages.add(indent(context) + "{ $limit: " + ir.getLimit() + " }");
        }

        // $project
        String project = projectStageBuilder.build(ir, context);
        if (project != null) stages.add(project);

        return stages;
    }

    private List<ConditionNode> extractJoinConditions(SqlToMongoIR ir) {
        List<ConditionNode> conditions = new ArrayList<>();
        for (var join : ir.getJoins()) {
            if (join.getJoinCondition() != null) {
                conditions.add(join.getJoinCondition());
            }
        }
        return conditions;
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
                String stage = lookupStageBuilder.buildCorrelatedSubqueryLookup(
                        subq, context.nextSubqueryName(), context);
                if (stage != null) stages.add(stage);
            }
        }

        return stages;
    }

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
        return "  ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
