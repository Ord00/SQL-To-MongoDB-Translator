package sql.to.mongodb.translator.base;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final List<String> pendingStages = new ArrayList<>();
    private boolean insideSubquery = false;
    private int subqueryLevel = 0;
    private final Map<String, String> subqueryArrayNames = new HashMap<>();

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

    public void addStages(List<String> stages) {
        if (stages != null) {
            pendingStages.addAll(stages);
        }
    }

    public List<String> getAndClearPendingStages() {
        List<String> result = new ArrayList<>(pendingStages);
        pendingStages.clear();
        return result;
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

    private String subqueryKey(Object subqueryRef) {
        return "subq@" + System.identityHashCode(subqueryRef);
    }
}
