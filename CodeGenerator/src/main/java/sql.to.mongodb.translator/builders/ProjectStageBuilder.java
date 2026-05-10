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
        StringBuilder project = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        project.append(context.getIndent()).append("$project: {\n");
        context.increaseIndent();

        boolean hasId = ir.getProjectionFields().stream()
                .anyMatch(p -> p instanceof ProjectionField f && "_id".equals(f.getField()));
        if (!hasId) {
            project.append(context.getIndent()).append("_id: 0,\n");
        }

        List<String> fields = new ArrayList<>();
        for (Projectionable proj : ir.getProjectionFields()) {
            String field = projectionTranslator.translate(proj, context, false);
            if (!field.isEmpty()) {
                fields.add(field);
            }
        }

        project.append(String.join(",\n", fields));
        context.decreaseIndent();
        project.append("\n").append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        project.append(indent(context)).append("}");

        return project.toString();
    }

    /**
     * Построение проекции для find() запроса
     * @param ir промежуточное представление
     * @return строка проекции для find()
     */
    private String buildFindProjection(SqlToMongoIR ir) {
        List<String> fields = new ArrayList<>();
        for (Projectionable proj : ir.getProjectionFields()) {
            if (proj instanceof ProjectionField pf) {
                String name = pf.getAlias() != null ? pf.getAlias() : pf.getField();
                if (!pf.isAllFields()) {
                    fields.add(name + ": 1");
                }
            }
            // Для find() запроса агрегатные функции и подзапросы не поддерживаются
            // поэтому игнорируем AggregateProjection и SubqueryProjection
        }
        if (fields.isEmpty()) return null;
        return "{ " + String.join(", ", fields) + " }";
    }

    public void addProjectionStages(SqlToMongoIR subIR,
                                    GenerationContext context,
                                    List<String> pipelineStages) throws CodeGenerationException {
        if (subIR == null || subIR.getProjectionFields().isEmpty()) {
            return;
        }

        // Проверяем, есть ли COUNT(DISTINCT)
        boolean hasDistinctCount = false;
        for (Projectionable proj : subIR.getProjectionFields()) {
            if (proj instanceof AggregateProjection agg && agg.isDistinct()) {
                hasDistinctCount = true;
                break;
            }
        }

        // TODO
        if (hasDistinctCount) {
            // Добавляем $project с $setDifference и $project с $size
            pipelineStages.add("{ $project: { teams: { $setDifference: [ \"$teams\", [null] ] } } }");
            pipelineStages.add("{ $project: { teamCount: { $size: \"$teams\" } } }");
            return;
        }

        // Обычная проекция
        StringBuilder project = new StringBuilder("{ $project: { _id: 0");
        for (Projectionable proj : subIR.getProjectionFields()) {
            if (proj instanceof ProjectionField pf) {
                String fieldName = pf.getAlias() != null ? pf.getAlias() : pf.getField();
                project.append(", ").append(fieldName).append(": 1");
            }
        }
        project.append(" } }");
        pipelineStages.add(project.toString());
    }

    public String buildAddFieldsWithMap(String arrayName,
                                        String sourceField,
                                        String valueField,
                                        GenerationContext context) {
        StringBuilder addFields = new StringBuilder();
        addFields.append(indent(context)).append("{\n");
        context.increaseIndent();
        addFields.append(context.getIndent()).append("$addFields: {\n");
        context.increaseIndent();
        addFields.append(context.getIndent()).append(arrayName).append(": {\n");
        addFields.append(context.getIndent()).append("    $map: {\n");
        addFields.append(context.getIndent()).append("        input: \"$").append(sourceField).append("\",\n");
        addFields.append(context.getIndent()).append("        as: \"item\",\n");
        addFields.append(context.getIndent()).append("        in: \"$$item.").append(valueField).append("\"\n");
        addFields.append(context.getIndent()).append("    }\n");
        addFields.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        addFields.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        addFields.append(indent(context)).append("}");
        return addFields.toString();
    }

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}