package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinTable;

import java.util.List;
import java.util.stream.Collectors;

import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.DOWN;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.NONE;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.UP;
import static sql.to.mongodb.translator.helpers.FormatHelper.indent;

@Component
public class LookupStageBuilder {

    public LookupStageBuilder() {
    }

    public String buildSimplePipelineLookup(String fromCollection,
                                            String asName,
                                            List<String> pipelineStages,
                                            GenerationContext context) {
        StringBuilder lookup = new StringBuilder(indent(UP, context)).append("{\n");
        lookup.append(indent(UP, context)).append("$lookup: {\n");
        lookup.append(indent(NONE, context)).append("from: \"").append(fromCollection).append("\",\n");
        lookup.append(indent(UP, context)).append("pipeline: [\n");

        for (int i = 0; i < pipelineStages.size(); i++) {
            lookup.append(indent(NONE, context)).append(pipelineStages.get(i));
            if (i < pipelineStages.size() - 1) lookup.append(",\n");
            else lookup.append("\n");
        }

        return buildCloseLookup(asName, context, lookup);
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

        String lookup = indent(UP, context) + "{\n" +
                indent(UP, context) + "$lookup: {\n" +
                indent(NONE, context) + "from: \"" + rightTable + "\",\n" +
                indent(NONE, context) + "localField: \"" + localField + "\",\n" +
                indent(NONE, context) + "foreignField: \"" + foreignField + "\",\n" +
                indent(DOWN, context) + "as: \"" + as + "\"\n" +
                indent(DOWN, context) + "}\n" +
                indent(NONE, context) + "}";

        boolean preserveNull = join.getType() == JoinInfo.JoinType.LEFT
                || join.getType() == JoinInfo.JoinType.RIGHT
                || join.getType() == JoinInfo.JoinType.FULL;
        String unwind = buildUnwind(as, preserveNull, context);
        return lookup + ",\n" + unwind;
    }

    public String buildLookupWithPipeline(String fromCollection,
                                          String asName,
                                          List<String> pipelineStages,
                                          GenerationContext context) {
        context.setIndentLevel(1 + 3 * (context.getSubqueryLevel() - 1));
        StringBuilder lookup = new StringBuilder(indent(UP, context)).append("{\n");
        lookup.append(indent(UP, context)).append("$lookup: {\n");
        lookup.append(indent(NONE, context)).append("from: \"").append(fromCollection).append("\",\n");

        lookup.append(indent(UP, context)).append("let: {\n");
        lookup.append(buildLetVariables(context)).append("\n");
        context.decreaseIndent();
        lookup.append(indent(NONE, context)).append("},\n");

        context.clearCorrelationVariables();

        lookup.append(indent(UP, context)).append("pipeline: [\n");

        // Добавляем все стадии подзапроса
        for (int i = 0; i < pipelineStages.size(); i++) {
            String stage = pipelineStages.get(i);
            lookup.append(stage);
            if (i < pipelineStages.size() - 1) lookup.append(",\n");
            else lookup.append("\n");
        }

        return buildCloseLookup(asName, context, lookup);
    }

    private String buildCloseLookup(String asName, GenerationContext context, StringBuilder lookup) {
        context.decreaseIndent();
        lookup.append(indent(NONE, context)).append("],\n");
        lookup.append(indent(DOWN, context)).append("as: \"").append(asName).append("\"\n");
        lookup.append(indent(DOWN, context)).append("}\n");
        lookup.append(indent(NONE, context)).append("}");
        return lookup.toString();
    }

    private String buildLetVariables(GenerationContext context) {
        return context.getCorrelationVariables().values().stream()
                .map(correlationVariable ->
                        indent(NONE, context) + correlationVariable.getMongoName()
                                + ": \"$" + correlationVariable.getLink() + "\"")
                .collect(Collectors.joining(",\n"));
    }

    public String buildUnwind(String path, boolean preserveNull, GenerationContext context) {
        if (preserveNull) {
            return indent(UP, context) + "{\n" +
                    indent(UP, context) + "$unwind: {\n" +
                    indent(NONE, context) + "path: \"$" + path + "\",\n" +
                    indent(DOWN, context) + "preserveNullAndEmptyArrays: true\n" +
                    indent(DOWN, context) + "}\n" +
                    indent(NONE, context) + "}";
        }

        return indent(UP, context) + "{\n" +
                indent(DOWN, context) + "$unwind: \"$" + path + "\"\n" +
                indent(NONE, context) + "}";
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
        return field.getSource() != null
                && (field.getSource().equals(rightAlias) || field.getSource().equals(rightTable));
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