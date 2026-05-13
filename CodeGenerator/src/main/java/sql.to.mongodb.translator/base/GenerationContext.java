package sql.to.mongodb.translator.base;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.code.generator.CorrelationVariable;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

@Getter
@Setter
public class GenerationContext {
    private int variableCounter = 0;
    private int subqueryCounter = 0;
    private int correlationCounter = 0;
    private int subqueryLevel = 0;
    private int indentLevel = 0;
    private boolean useAggregationSyntax = false;
    private boolean insideSubquery = false;
    private final Map<String, String> subqueryResults = new HashMap<>();
    private final Map<String, String> sourcePathMap = new HashMap<>();
    private final Map<String, CorrelationVariable> correlationVariables = new HashMap<>();
    private List<CorrelationCondition> correlationConditions = new ArrayList<>();
    private final Stack<Map<String, String>> aliasesStack = new Stack<>();

    public String getVariableName() {
        return "var" + (variableCounter);
    }

    public String nextVariableName() {
        return "var" + (++variableCounter);
    }

    public String getSubqueryName(Object subqueryRef) {
        return subqueryResults.get(subqueryKey(subqueryRef));
    }

    public String getOrCreateSubqueryName(Object subqueryRef) {
        String key = subqueryKey(subqueryRef);
        return subqueryResults.computeIfAbsent(key, ignored -> nextSubqueryName());
    }

    public String nextSubqueryName() {
        return "subquery_" + (++subqueryCounter);
    }

    public void increaseIndent() {
        indentLevel++;
    }

    public void decreaseIndent() {
        if (indentLevel > 0) indentLevel--;
    }

    public void enterSubquery() {
        subqueryLevel++;
    }

    public void leaveSubquery() {
        subqueryLevel--;
    }

    public void mapSourcePath(String source, String pathPrefix) {
        if (source == null || source.isBlank()) {
            return;
        }
        sourcePathMap.put(source, pathPrefix == null ? "" : pathPrefix);
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

    public void addCorrelationVariable(String originalField, CorrelationVariable varName) {
        correlationVariables.put(originalField, varName);
    }

    public void clearCorrelationVariables() {
        correlationVariables.clear();
    }

    public void addCorrelation(CorrelationCondition correlationCondition) {
        correlationConditions.add(correlationCondition);
    }

    public boolean isCorrelationField(Field field) {
        if (field.getSource() == null) return false;
        String fullField = field.getSource() + "." + field.getField();
        return correlationVariables.containsKey(fullField);
    }

    public String getCorrelationVariableForField(Field field) {
        String fullField = field.getSource() + "." + field.getField();
        CorrelationVariable corVar = correlationVariables.get(fullField);
        return corVar.getMongoName();
    }

    public void pushAliases(Map<String, String> aliases) {
        aliasesStack.push(aliases);
    }

    public void popAliases() {
        aliasesStack.pop();
    }

    public Map<String, String> peekAliases() {
        return aliasesStack.peek();
    }

    private String subqueryKey(Object subqueryRef) {
        return "subq@" + System.identityHashCode(subqueryRef);
    }
}
