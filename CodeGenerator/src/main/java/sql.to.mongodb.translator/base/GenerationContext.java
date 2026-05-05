package sql.to.mongodb.translator.base;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class GenerationContext {
    private int indentLevel = 0;
    private int subqueryCounter = 0;
    private int correlationCounter = 0;
    private final Map<String, String> subqueryResults = new HashMap<>();
    private final Map<String, String> sourcePathMap = new HashMap<>();
    private boolean useAggregationSyntax = false;

    public String nextSubqueryName() {
        return "subquery_" + (++subqueryCounter);
    }

    public String nextCorrelationName() {
        return "corr_" + (++correlationCounter);
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

    private String subqueryKey(Object subqueryRef) {
        return "subq@" + System.identityHashCode(subqueryRef);
    }
}
