package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
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
public class ProjectStageBuilder {

    private final ProjectionTranslator projectionTranslator;

    public ProjectStageBuilder(ProjectionTranslator projectionTranslator) {
        this.projectionTranslator = projectionTranslator;
    }

    public String build(SqlToMongoIR ir, GenerationContext context) throws CodeGenerationException {
        if (ir.getProjectionFields().isEmpty()) {
            return null;
        }

        // Для find() запроса
        if (!context.isUseAggregationSyntax()) {
            return buildFindProjection(ir);
        }

        // Для aggregation pipeline
        StringBuilder project = new StringBuilder(indent(UP, context)).append("{\n");
        project.append(indent(UP, context)).append("$project: {\n");

        boolean hasId = ir.getProjectionFields().stream()
                .anyMatch(p -> p instanceof ProjectionField f && "_id".equals(f.getField()));
        if (!hasId) {
            project.append(indent(NONE, context)).append("_id: 0,\n");
        }

        List<String> fields = new ArrayList<>();

        if (!context.isInsideSubquery()) {
            for (Projectionable proj : ir.getProjectionFields()) {
                if (proj instanceof ProjectionField pf) {
                    String name = pf.getAlias() != null ? pf.getAlias() : pf.getField();
                    fields.add(indent(NONE, context) + name + ": 1");
                }
            }
        } else {
            for (Projectionable proj : ir.getProjectionFields()) {
                String field = projectionTranslator.translate(proj, context, false);
                if (!field.isEmpty()) {
                    fields.add(field);
                }
            }
        }

        project.append(String.join(",\n", fields));
        context.decreaseIndent();
        project.append("\n").append(indent(DOWN, context)).append("}\n");
        project.append(indent(NONE, context)).append("}");

        return project.toString();
    }

    public void addProjectionStages(SqlToMongoIR ir,
                                    List<String> pipelineStages,
                                    GenerationContext context) {
        if (ir == null) return;

        boolean hasDistinctCount = isDistinctCount(ir);

        if (hasDistinctCount) {
            String firstVar = context.getVariableName();

            // $project с $setDifference
            StringBuilder sb = new StringBuilder(indent(UP, context)).append("{\n");
            sb.append(indent(UP, context)).append("$project: {\n");
            sb.append(indent(UP, context)).append(firstVar).append(": {\n");
            sb.append(indent(DOWN, context)).append("$setDifference: [\"$").append(firstVar).append("\", [null]]\n");
            sb.append(indent(DOWN, context)).append("}\n");
            sb.append(indent(DOWN, context)).append("}\n");
            sb.append(indent(NONE, context)).append("}");
            pipelineStages.add(sb.toString());
            // $project с $size
            sb.setLength(0);
            String secondVar = context.nextVariableName();

            sb.append(indent(UP, context)).append("{\n");
            sb.append(indent(UP, context)).append("$project: {\n");
            sb.append(indent(UP, context)).append(secondVar).append(": {\n");
            sb.append(indent(DOWN, context)).append("$size: \"$").append(firstVar).append("\"\n");
            sb.append(indent(DOWN, context)).append("}\n");
            sb.append(indent(DOWN, context)).append("}\n");
            sb.append(indent(NONE, context)).append("}");
            pipelineStages.add(sb.toString());
        }
    }

    private static boolean isDistinctCount(SqlToMongoIR ir) {
        boolean hasDistinctCount = false;

        // Проверяем проекции
        for (Projectionable proj : ir.getProjectionFields()) {
            if (proj instanceof AggregateProjection agg &&
                    agg.isDistinct() &&
                    agg.getType() == AggregateProjection.AggregateType.COUNT) {
                hasDistinctCount = true;
                break;
            }
        }

        // Если не нашли в проекциях, проверяем HAVING (по флагам)
        if (!hasDistinctCount && ir.isHasAggregateFunctions() && ir.isHasGroupBy()) {
            hasDistinctCount = true;
        }
        return hasDistinctCount;
    }

    private String buildFindProjection(SqlToMongoIR ir) {
        List<String> fields = new ArrayList<>();
        for (Projectionable proj : ir.getProjectionFields()) {
            if (proj instanceof ProjectionField pf) {
                String name = pf.getAlias() != null ? pf.getAlias() : pf.getField();
                if (!pf.isAllFields()) {
                    fields.add(name + ": 1");
                }
            }
        }
        if (fields.isEmpty()) return null;
        return "{ " + String.join(", ", fields) + " }";
    }

    public String buildAddFieldsWithMap(String arrayName,
                                        String sourceField,
                                        String valueField,
                                        GenerationContext context) {
        return indent(UP, context) + "{\n" +
                indent(UP, context) + "$addFields: {\n" +
                indent(UP, context) + arrayName + ": {\n" +
                indent(UP, context) + "$map: {\n" +
                indent(NONE, context) + "input: \"$" + sourceField + "\",\n" +
                indent(NONE, context) + "as: \"item\",\n" +
                indent(DOWN, context) + "in: \"$$item." + valueField + "\"\n" +
                indent(DOWN, context) + "}\n" +
                indent(DOWN, context) + "}\n" +
                indent(DOWN, context) + "}\n" +
                indent(NONE, context) + "}";
    }
}