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
            group.append("\"$").append(field.getField()).append("\"");
        } else {
            group.append("{\n");
            context.increaseIndent();
            for (int i = 0; i < ir.getGroupByFields().size(); i++) {
                GroupByField field = ir.getGroupByFields().get(i);
                group.append(context.getIndent())
                        .append(field.getField())
                        .append(": \"$").append(field.getField()).append("\"");
                if (i < ir.getGroupByFields().size() - 1) group.append(",\n");
            }
            context.decreaseIndent();
            group.append("\n").append(context.getIndent()).append("}");
        }
    }

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
