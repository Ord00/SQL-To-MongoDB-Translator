package sql.to.mongodb.translator.base;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.Field;


import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class GenerationContext {
    private int indentLevel = 0;
    private int variableCounter = 0;
    private int subqueryCounter = 0;
    private int correlationCounter = 0;
    private final Map<String, String> subqueryResults = new HashMap<>();
    private final Map<String, String> sourcePathMap = new HashMap<>();
    private boolean useAggregationSyntax = false;
    private boolean insideSubquery = false;
    private int subqueryLevel = 0;
    private final Map<String, String> subqueryArrayNames = new HashMap<>();
    private final Map<String, String> correlationVariables = new HashMap<>();

    public String getVariableName() {
        return "var" + (variableCounter);
    }

    public String nextVariableName() {
        return "var" + (++variableCounter);
    }

    public String nextSubqueryName() {
        return "subquery_" + (++subqueryCounter);
    }

    public String nextCorrelationName() {
        return "corr_" + (++correlationCounter);
    }

    public String getVariableNameForAggregate(AggregateProjection agg) {
        if (agg.getAlias() != null && !agg.getAlias().isBlank()) {
            return agg.getAlias();
        }
        return nextVariableName();
    }

    public String getOrCreateSubqueryName(Object subqueryRef) {
        String key = subqueryKey(subqueryRef);
        return subqueryResults.computeIfAbsent(key, ignored -> nextSubqueryName());
    }

    public String getSubqueryName(Object subqueryRef) {
        return subqueryResults.get(subqueryKey(subqueryRef));
    }

    public void increaseIndent() {
        indentLevel++;
    }

    public void decreaseIndent() {
        if (indentLevel > 0) indentLevel--;
    }

    public String getIndent() {
        return "    ".repeat(Math.max(0, indentLevel));
    }

    public void mapSourcePath(String source, String pathPrefix) {
        if (source == null || source.isBlank()) {
            return;
        }
        sourcePathMap.put(source, pathPrefix == null ? "" : pathPrefix);
    }

    public void enterSubquery() {
        subqueryLevel++;
    }

    public void exitSubquery() {
        if (subqueryLevel > 0) subqueryLevel--;
    }

    public String getSubqueryArrayName(String subqueryId) {
        return subqueryArrayNames.computeIfAbsent(subqueryId,
                id -> "subquery_" + (++subqueryCounter) + "Array");
    }

    public String resolveFieldPath(String source, String field) {
        if (source == null || source.isBlank()) {
            return field;
        }
        if (sourcePathMap.containsKey(source)) {
            String mapped = sourcePathMap.get(source);
            if (mapped == null || mapped.isBlank()) {
                return field;
            }
            return mapped + "." + field;
        }
        String prefix = sourcePathMap.get(source);
        if (prefix == null) {
            return source + "." + field;
        }
        return prefix.isBlank() ? field : prefix + "." + field;
    }

    public void addCorrelation(String originalField, String varName) {
        correlationVariables.put(originalField, varName);
    }

    public String getCorrelationVariable(String originalField) {
        return correlationVariables.get(originalField);
    }

    public boolean isCorrelationField(Field field) {
        if (field.getSource() == null) return false;
        String fullField = field.getSource() + "." + field.getField();
        return correlationVariables.containsKey(fullField) ||
                correlationVariables.containsKey(field.getField());
    }

    public String getCorrelationVariableForField(Field field) {
        String fullField = field.getSource() + "." + field.getField();
        String varName = correlationVariables.get(fullField);
        if (varName == null) {
            varName = correlationVariables.get(field.getField());
        }
        return varName;
    }

    private String subqueryKey(Object subqueryRef) {
        return "subq@" + System.identityHashCode(subqueryRef);
    }
}
