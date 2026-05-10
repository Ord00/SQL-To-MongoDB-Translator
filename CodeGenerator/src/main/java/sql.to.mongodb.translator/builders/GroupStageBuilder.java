package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.GroupByField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.projection.Projectionable;
import sql.to.mongodb.translator.translators.ProjectionTranslator;

import java.util.ArrayList;
import java.util.List;

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

        StringBuilder group = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        group.append(context.getIndent()).append("$group: {\n");
        context.increaseIndent();

        // _id
        group.append(context.getIndent()).append("_id: ");
        buildGroupId(ir, group, context);

        // Агрегации
        List<String> aggregations = new ArrayList<>();
        for (Projectionable proj : ir.getProjectionFields()) {
            if (proj instanceof AggregateProjection agg) {
                String aggStr = projectionTranslator.translate(agg, context, false);
                if (!aggStr.isEmpty()) {
                    aggregations.add(aggStr.replace(context.getIndent(), context.getIndent()));
                }
            }
        }

        if (!aggregations.isEmpty()) {
            group.append(",\n").append(String.join(",\n", aggregations));
        }

        context.decreaseIndent();
        group.append("\n").append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        group.append(indent(context)).append("}");

        return group.toString();
    }

    private void buildGroupId(SqlToMongoIR ir, StringBuilder group, GenerationContext context) {
        if (ir.getGroupByFields().isEmpty()) {
            group.append("null");
        } else if (ir.getGroupByFields().size() == 1) {
            GroupByField field = ir.getGroupByFields().getFirst();
            // Используем resolveFieldPath для правильного разрешения пути поля
            String resolved = context.resolveFieldPath(field.getSource(), field.getField());
            group.append("\"$").append(resolved).append("\"");
        } else {
            group.append("{\n");
            context.increaseIndent();
            for (int i = 0; i < ir.getGroupByFields().size(); i++) {
                GroupByField field = ir.getGroupByFields().get(i);
                String resolved = context.resolveFieldPath(field.getSource(), field.getField());
                group.append(context.getIndent())
                        .append(field.getField())
                        .append(": \"$").append(resolved).append("\"");
                if (i < ir.getGroupByFields().size() - 1) group.append(",\n");
            }
            context.decreaseIndent();
            group.append("\n").append(context.getIndent()).append("}");
        }
    }

    /**
     * Построение $group стадии для подзапроса с использованием контекста
     * @param subIR IR подзапроса
     * @param context контекст генерации (используется для отступов и синтаксиса)
     * @return строка $group стадии или null если GROUP BY нет
     */
    public String buildForSubquery(SqlToMongoIR subIR,
                                   GenerationContext context) {
        if (subIR == null || !subIR.isHasGroupBy()) {
            return null;
        }

        StringBuilder group = new StringBuilder();

        // Добавляем отступ если в агрегационном контексте
        if (context.isUseAggregationSyntax()) {
            group.append(indent(context));
        }

        group.append("{ $group: { _id: ");

        if (subIR.getGroupByFields().isEmpty()) {
            group.append("null");
        } else if (subIR.getGroupByFields().size() == 1) {
            group.append("\"$").append(subIR.getGroupByFields().getFirst()).append("\"");
        } else {
            group.append("{\n");
            int savedIndent = context.getIndentLevel();
            context.setIndentLevel(savedIndent + 1);

            for (int i = 0; i < subIR.getGroupByFields().size(); i++) {
                GroupByField field = subIR.getGroupByFields().get(i);
                group.append(indent(context))
                        .append(field.getSource())
                        .append(": \"$")
                        .append(field.getField())
                        .append("\"");
                if (i < subIR.getGroupByFields().size() - 1) group.append(",\n");
            }

            context.setIndentLevel(savedIndent);
            group.append("\n").append(indent(context)).append("}");
        }

        // Добавляем агрегации для подзапроса
        boolean hasAggregations = false;
        for (Projectionable proj : subIR.getProjectionFields()) {
            if (proj instanceof AggregateProjection agg) {
                if (!hasAggregations) {
                    hasAggregations = true;
                }
                String field = agg.getField() != null ? agg.getField().getField() : null;
                if (field != null && !field.isEmpty()) {
                    group.append(", ").append(agg.getType().name().toLowerCase())
                            .append(": { $").append(agg.getType().name().toLowerCase())
                            .append(": \"$").append(field).append("\" }");
                }
            }
        }

        group.append(" } }");
        return group.toString();
    }

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
