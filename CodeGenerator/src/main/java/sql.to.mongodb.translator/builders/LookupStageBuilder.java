package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinTable;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LookupStageBuilder {

    public LookupStageBuilder() {
    }

    public String buildSimplePipelineLookup(String fromCollection,
                                            String asName,
                                            List<String> pipelineStages,
                                            GenerationContext context) {
        StringBuilder lookup = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        lookup.append(indent(context)).append("$lookup: {\n");
        context.increaseIndent();
        lookup.append(indent(context)).append("from: \"").append(fromCollection).append("\",\n");
        lookup.append(indent(context)).append("pipeline: [\n");
        context.increaseIndent();

        for (int i = 0; i < pipelineStages.size(); i++) {
            lookup.append(indent(context)).append(pipelineStages.get(i));
            if (i < pipelineStages.size() - 1) lookup.append(",\n");
            else lookup.append("\n");
        }

        context.decreaseIndent();
        lookup.append(indent(context)).append("],\n");
        lookup.append(indent(context)).append("as: \"").append(asName).append("\"\n");
        context.decreaseIndent();
        lookup.append(indent(context)).append("}\n");
        context.decreaseIndent();
        lookup.append(indent(context)).append("}");
        return lookup.toString();
    }

    public String buildSimpleLookup(JoinInfo join, GenerationContext context) {
        String rightTable = join.getRight() instanceof JoinTable t ? t.getValue() : "subquery";
        String as;
        if (join.getRight() != null && join.getRight().getAlias() != null) {
            as = join.getRight().getAlias();
        } else {
            as = toJoinAlias(rightTable);
        }

        String localField = "_id";
        String foreignField = "_id";
        if (join.getJoinCondition() instanceof Comparison comp) {
            String rightAlias = join.getRight() != null ? join.getRight().getAlias() : null;
            JoinFields joinFields = resolveJoinFields(comp, rightAlias, rightTable, context);
            localField = joinFields.localField;
            foreignField = joinFields.foreignField;
        }

        StringBuilder lookup = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        lookup.append(context.getIndent()).append("$lookup: {\n");
        context.increaseIndent();
        lookup.append(context.getIndent()).append("from: \"").append(rightTable).append("\",\n");
        lookup.append(context.getIndent()).append("localField: \"").append(localField).append("\",\n");
        lookup.append(context.getIndent()).append("foreignField: \"").append(foreignField).append("\",\n");
        lookup.append(context.getIndent()).append("as: \"").append(as).append("\"\n");
        context.decreaseIndent();
        lookup.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        lookup.append(indent(context)).append("}");

        boolean preserveNull = join.getType() == JoinInfo.JoinType.LEFT
                || join.getType() == JoinInfo.JoinType.RIGHT
                || join.getType() == JoinInfo.JoinType.FULL;
        String unwind = buildUnwind(as, preserveNull, context);
        return lookup + ",\n" + unwind;
    }

    public String buildLookupWithPipeline(String fromCollection,
                                          String asName,
                                          List<String> pipelineStages,
                                          List<CorrelationCondition> correlations,
                                          GenerationContext context) {
        context.setIndentLevel(1);
        StringBuilder lookup = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        lookup.append(context.getIndent()).append("$lookup: {\n");
        context.increaseIndent();
        lookup.append(context.getIndent()).append("from: \"").append(fromCollection).append("\",\n");

        // Добавляем let variables если есть корреляции
        if (correlations != null && !correlations.isEmpty()) {
            lookup.append(context.getIndent())
                    .append("let: { ")
                    .append(buildLetVariables(correlations, context))
                    .append(" },\n");
        }

        lookup.append(context.getIndent()).append("pipeline: [\n");
        context.increaseIndent();

        // Добавляем все стадии подзапроса
        for (int i = 0; i < pipelineStages.size(); i++) {
            String stage = pipelineStages.get(i);
            lookup.append(stage);
            if (i < pipelineStages.size() - 1) lookup.append(",\n");
            else lookup.append("\n");
        }

        context.decreaseIndent();
        lookup.append(context.getIndent()).append("],\n");
        lookup.append(context.getIndent()).append("as: \"").append(asName).append("\"\n");
        context.decreaseIndent();
        lookup.append(context.getIndent()).append("}\n");
        context.decreaseIndent();
        lookup.append(indent(context)).append("}");
        return lookup.toString();
    }

    private String buildLetVariables(List<CorrelationCondition> correlations, GenerationContext context) {
        return correlations.stream()
                .map(c -> {
                    String outerField = c.getOuterField().getField();
                    String resolvedPath = resolveFieldPath(c.getOuterField(), context);
                    return outerField + ": \"$" + resolvedPath + "\"";
                })
                .collect(Collectors.joining(", "));
    }

    private String resolveFieldPath(Field field, GenerationContext context) {
        if (field.getSource() == null || field.getSource().isBlank()) {
            return field.getField();
        }
        String mapped = context.getSourcePathMap().get(field.getSource());
        if (mapped != null && !mapped.isBlank()) {
            return mapped + "." + field.getField();
        } else {
            return field.getField();
        }
    }

    public String buildCorrelationMatch(List<CorrelationCondition> correlations,
                                        GenerationContext context) {
        if (correlations == null || correlations.isEmpty()) return null;

        StringBuilder match = new StringBuilder();

        match.append(indent(context)).append("{\n");
        context.increaseIndent();
        match.append(indent(context)).append("$match: {\n");
        context.increaseIndent();
        match.append(indent(context)).append("$expr: {\n");
        context.increaseIndent();

        boolean isMultiCor = false;
        if (correlations.size() > 1) {
            isMultiCor = true;
            match.append(indent(context)).append("$and: [\n");
            context.increaseIndent();
        }

        for (int i = 0; i < correlations.size(); i++) {
            CorrelationCondition c = correlations.get(i);

            match.append(indent(context));
            if (isMultiCor) {
                match.append("{ ");
            }
            match.append("$eq: [\"$")
                    .append(c.getInnerField().getField())
                    .append("\", \"$$")
                    .append(c.getOuterField().getField())
                    .append("\"]");

            context.decreaseIndent();

            if (i < correlations.size() - 1) match.append(",");
            match.append("\n");
        }

        if (isMultiCor) {
            match.append(indent(context)).append("]\n");
            context.decreaseIndent();
        }

        match.append(indent(context)).append("}\n");
        context.decreaseIndent();
        match.append(indent(context)).append("}\n");
        context.decreaseIndent();
        match.append(indent(context)).append("}");

        return match.toString();
    }

    public String buildUnwind(String path, boolean preserveNull, GenerationContext context) {
        if (preserveNull) {
            StringBuilder unwind = new StringBuilder(indent(context)).append("{\n");
            context.increaseIndent();
            unwind.append(context.getIndent()).append("$unwind: {\n");
            context.increaseIndent();
            unwind.append(context.getIndent()).append("path: \"$").append(path).append("\",\n");
            unwind.append(context.getIndent()).append("preserveNullAndEmptyArrays: true\n");
            context.decreaseIndent();
            unwind.append(context.getIndent()).append("}\n");
            context.decreaseIndent();
            unwind.append(indent(context)).append("}");
            return unwind.toString();
        }

        StringBuilder unwind = new StringBuilder(indent(context)).append("{\n");
        context.increaseIndent();
        unwind.append(indent(context)).append("$unwind: \"$").append(path).append("\"\n");
        context.decreaseIndent();
        unwind.append(indent(context)).append("}");

        return unwind.toString();
    }

    private String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }

    private JoinFields resolveJoinFields(Comparison comparison,
                                         String rightAlias,
                                         String rightTable,
                                         GenerationContext context) {
        if (comparison.getOperand() instanceof Field left && comparison.getValue() instanceof Field right) {
            if (isRightField(right, rightAlias, rightTable)) {
                return new JoinFields(context.resolveFieldPath(left.getSource(), left.getField()), right.getField());
            }
            if (isRightField(left, rightAlias, rightTable)) {
                return new JoinFields(context.resolveFieldPath(right.getSource(), right.getField()), left.getField());
            }
        }
        return new JoinFields("_id", "_id");
    }

    private boolean isRightField(Field field, String rightAlias, String rightTable) {
        return field.getSource() != null && (field.getSource().equals(rightAlias) || field.getSource().equals(rightTable));
    }

    private String toJoinAlias(String table) {
        if (table == null || table.isBlank()) {
            return "join";
        }
        return Character.toLowerCase(table.charAt(0)) + table.substring(1) + "Join";
    }

    private record JoinFields(String localField, String foreignField) {
    }
}