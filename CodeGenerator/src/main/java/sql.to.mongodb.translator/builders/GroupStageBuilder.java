package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.Aggregate;
import sql.to.mongodb.translator.ir.GroupByField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.Projectionable;
import sql.to.mongodb.translator.translators.ProjectionTranslator;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.DOWN;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.NONE;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.UP;
import static sql.to.mongodb.translator.helpers.FormatHelper.indent;

@Component
public class GroupStageBuilder {

    private final ProjectionTranslator projectionTranslator;

    public GroupStageBuilder(ProjectionTranslator projectionTranslator) {
        this.projectionTranslator = projectionTranslator;
    }

    public String build(SqlToMongoIR ir, GenerationContext context) throws CodeGenerationException {
        if (!ir.isHasGroupBy() && !ir.isHasAggregateFunctions()) {
            return null;
        }

        StringBuilder group = new StringBuilder(indent(UP, context)).append("{\n");
        group.append(indent(UP, context)).append("$group: {\n");

        // _id
        group.append(indent(NONE, context)).append("_id: ");
        buildGroupId(ir, group, context);

        // Агрегации из проекции
        List<String> aggregations = new ArrayList<>();
        for (Projectionable proj : ir.getProjectionFields()) {
            if (proj instanceof AggregateProjection agg) {
                String aggStr = projectionTranslator.translate(agg, context, false);
                if (!aggStr.isEmpty()) {
                    aggregations.add(aggStr.replace(indent(NONE, context), indent(NONE, context)));
                }
            }
        }

        // Агрегации из HAVING
        List<AggregateProjection> havingAggregations = extractAggregationsFromHaving(ir.getHavingCondition(), context);
        for (AggregateProjection agg : havingAggregations) {
            String aggStr = projectionTranslator.translate(agg, context, false);
            if (!aggStr.isEmpty()) {
                aggregations.add(aggStr.replace(indent(NONE, context), indent(NONE, context)));
            }
        }

        if (!aggregations.isEmpty()) {
            group.append(",\n").append(String.join(",\n", aggregations));
        }

        context.decreaseIndent();
        group.append("\n").append(indent(DOWN, context)).append("}\n");
        group.append(indent(NONE, context)).append("}");

        return group.toString();
    }

    private void buildGroupId(SqlToMongoIR ir, StringBuilder group, GenerationContext context) {
        if (ir.getGroupByFields().isEmpty()) {
            group.append("null");
        } else if (ir.getGroupByFields().size() == 1) {
            GroupByField field = ir.getGroupByFields().getFirst();
            String resolved = context.resolveFieldPath(field.getSource(), field.getField());
            group.append("\"$").append(resolved).append("\"");
        } else {
            group.append("{\n");
            context.increaseIndent();
            for (int i = 0; i < ir.getGroupByFields().size(); i++) {
                GroupByField field = ir.getGroupByFields().get(i);
                group.append(indent(NONE, context))
                        .append(field.getField())
                        .append(": \"$").append(field.getField()).append("\"");
                if (i < ir.getGroupByFields().size() - 1) group.append(",\n");
            }
            context.decreaseIndent();
            group.append("\n").append(indent(NONE, context)).append("}");
        }
    }

    private List<AggregateProjection> extractAggregationsFromHaving(ConditionNode havingCondition,
                                                                    GenerationContext context) {
        List<AggregateProjection> aggregations = new ArrayList<>();
        if (havingCondition == null) return aggregations;

        extractAggregationsRecursive(havingCondition, aggregations, context);
        return aggregations;
    }

    private void extractAggregationsRecursive(ConditionNode node,
                                              List<AggregateProjection> aggregations,
                                              GenerationContext context) {
        if (node == null) return;

        if (node instanceof Comparison comp) {
            if (comp.getOperand() instanceof Aggregate agg) {
                AggregateProjection aggProj = new AggregateProjection();
                aggProj.setType(agg.getType());
                aggProj.setDistinct(agg.isDistinct());
                if (agg.getField() != null) {
                    aggProj.setField(new ProjectionField(
                            agg.getField().getSource(),
                            agg.getField().getField(),
                            null));
                }

                if (agg.isDistinct() && agg.getType() == Aggregate.AggregateType.COUNT) {
                    aggProj.setAlias(context.nextVariableName());
                }
                aggregations.add(aggProj);
            }
        }

        if (node instanceof LinkNode link) {
            for (ConditionNode child : link.getChildren()) {
                extractAggregationsRecursive(child, aggregations, context);
            }
        }
    }
}
