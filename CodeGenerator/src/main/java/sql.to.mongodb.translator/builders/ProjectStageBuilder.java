package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.CodeGenerationException;
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
        StringBuilder project = new StringBuilder(indent(context) + "{ $project: {\n");
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
        project.append("\n").append(indent(context)).append("} }");

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

    /**
     * Построение проекции для подзапроса (используется в LookupStageBuilder)
     * @param subIR IR подзапроса
     * @param context контекст генерации (используется для отступов и синтаксиса)
     * @return строка $project стадии
     */
    public String buildForSubquery(SqlToMongoIR subIR,
                                   GenerationContext context) throws CodeGenerationException {
        if (subIR == null || subIR.getProjectionFields().isEmpty()) {
            return null;
        }

        // Если поле-подзапрос и это не агрегация - простая проекция
        if (subIR.getProjectionFields().size() == 1) {
            Projectionable only = subIR.getProjectionFields().getFirst();
            if (only instanceof ProjectionField pf) {
                return "{ $project: { _id: 0, " + pf.getField() + ": 1 } }";
            }
            return "{ $project: { _id: 0, result: 1 } }";
        }

        // Сложная проекция с несколькими полями
        StringBuilder project = new StringBuilder("{ $project: { _id: 0");

        for (Projectionable proj : subIR.getProjectionFields()) {
            if (proj instanceof ProjectionField pf) {
                String fieldName = pf.getAlias() != null ? pf.getAlias() : pf.getField();
                project.append(", ").append(fieldName).append(": 1");
            } else if (proj instanceof AggregateProjection agg) {
                // Для подзапросов с агрегациями используем транслятор
                String aggStr = projectionTranslator.translate(agg, context, false);
                if (!aggStr.isEmpty()) {
                    String name = agg.getAlias() != null ? agg.getAlias() : agg.getType().name().toLowerCase();
                    project.append(", ").append(name).append(": 1");
                }
            }
        }

        project.append(" } }");
        return project.toString();
    }

    private String indent(GenerationContext context) {
        return "  ".repeat(Math.max(0, context.getIndentLevel()));
    }
}