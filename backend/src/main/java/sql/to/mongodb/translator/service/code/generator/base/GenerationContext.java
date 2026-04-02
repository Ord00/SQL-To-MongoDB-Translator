package sql.to.mongodb.translator.service.code.generator.base;

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
    private boolean useAggregationSyntax = false;

    public String nextSubqueryName() {
        return "subquery_" + (++subqueryCounter);
    }

    public String nextCorrelationName() {
        return "corr_" + (++correlationCounter);
    }

    public void increaseIndent() {
        indentLevel++;
    }

    public void decreaseIndent() {
        if (indentLevel > 0) indentLevel--;
    }

    public String getIndent() {
        return "  ".repeat(Math.max(0, indentLevel));
    }
}
