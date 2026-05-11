package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.code.generator.TranslationResult;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.projection.ProjectionField;

import java.util.ArrayList;
import java.util.List;

@Component
public class PipelineBuilder {
    private final MatchStageBuilder matchStageBuilder;
    private final LookupStageBuilder lookupStageBuilder;
    private final GroupStageBuilder groupStageBuilder;
    private final ProjectStageBuilder projectStageBuilder;
    private final SortStageBuilder sortStageBuilder;

    public PipelineBuilder(MatchStageBuilder matchStageBuilder,
                           LookupStageBuilder lookupStageBuilder,
                           GroupStageBuilder groupStageBuilder,
                           ProjectStageBuilder projectStageBuilder,
                           SortStageBuilder sortStageBuilder) {
        this.matchStageBuilder = matchStageBuilder;
        this.lookupStageBuilder = lookupStageBuilder;
        this.groupStageBuilder = groupStageBuilder;
        this.projectStageBuilder = projectStageBuilder;
        this.sortStageBuilder = sortStageBuilder;
    }

    public List<String> buildStages(SqlToMongoIR ir,
                                    GenerationContext context) throws CodeGenerationException {

        List<String> stages = new ArrayList<>();

        initSourceMappings(ir, context);

        // Обработка JOIN
        for (var join : ir.getJoins()) {
            String lookup = lookupStageBuilder.buildSimpleLookup(join, context);
            if (lookup != null) stages.add(lookup);
        }

        // WHERE - получаем результат со стадиями и условием
        TranslationResult whereResult = matchStageBuilder.buildWhere(ir, context);
        stages.addAll(whereResult.getPrerequisiteStages());
        String where = whereResult.getCondition();
        if (where != null) stages.add(where);

        // GROUP BY
        String group = groupStageBuilder.build(ir, context);
        if (group != null) stages.add(group);

        // HAVING
        TranslationResult havingResult = matchStageBuilder.buildHaving(ir, context);
        stages.addAll(havingResult.getPrerequisiteStages());
        String having = havingResult.getCondition();
        if (having != null) stages.add(having);

        // SORT
        String sort = sortStageBuilder.build(ir, context);
        if (sort != null) stages.add(sort);

        // LIMIT / OFFSET
        if (ir.getOffset() != null) {
            stages.add(indent(context) + "{ $skip: " + ir.getOffset() + " }");
        }
        if (ir.getLimit() != null) {
            stages.add(indent(context) + "{ $limit: " + ir.getLimit() + " }");
        }

        // DISTINCT без агрегации
        if (ir.isDistinct() && !ir.isHasGroupBy() && !ir.isHasAggregateFunctions()) {
            stages.add(buildDistinctGroup(ir, context));
            stages.add(buildDistinctProject(ir, context));
            return stages;
        }

        // PROJECT
        String project = projectStageBuilder.build(ir, context);
        if (project != null) stages.add(project);

        return stages;
    }

    private void initSourceMappings(SqlToMongoIR ir, GenerationContext context) {
        context.mapSourcePath(ir.getMainCollection(), "");
        ir.getAliases().forEach((alias, table) -> {
            if (table.equals(ir.getMainCollection())) {
                context.mapSourcePath(alias, "");
            }
            context.mapSourcePath(table, table.equals(ir.getMainCollection()) ? "" : alias);
        });
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

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}