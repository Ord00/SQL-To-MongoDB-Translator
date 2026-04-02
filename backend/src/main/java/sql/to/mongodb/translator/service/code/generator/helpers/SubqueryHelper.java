package sql.to.mongodb.translator.service.code.generator.helpers;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.intermediate.representation.model.CorrelationSubquery;
import sql.to.mongodb.translator.service.intermediate.representation.model.Subquery;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.CorrelationCondition;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SubqueryHelper {

    /**
     * Проверяет, является ли подзапрос коррелированным
     */
    public boolean isCorrelated(Subquery subquery) {
        if (subquery == null) return false;
        // CorrelationSubquery может быть как самостоятельным классом, так и предком SubqueryProjection
        if (subquery instanceof CorrelationSubquery cs) {
            return cs.getCorrelations() != null && !cs.getCorrelations().isEmpty();
        }
        return false;
    }

    /**
     * Построение let-переменных для $lookup
     */
    public String buildLetVariables(List<CorrelationCondition> correlations) {
        if (correlations == null || correlations.isEmpty()) return "";
        return correlations.stream()
                .map(c -> {
                    String outerField = c.getOuterField().getField();
                    return outerField + ": \"$" + outerField + "\"";
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Построение условия корреляции для pipeline
     */
    public String buildCorrelationMatch(List<CorrelationCondition> correlations,
                                        GenerationContext context) {
        if (correlations == null || correlations.isEmpty()) return null;

        StringBuilder match = new StringBuilder("{ $match: {\n");
        context.increaseIndent();
        match.append(context.getIndent()).append("$expr: { $and: [\n");
        context.increaseIndent();

        for (int i = 0; i < correlations.size(); i++) {
            CorrelationCondition corr = correlations.get(i);
            String innerField = corr.getInnerField().getField();
            String outerField = corr.getOuterField().getField();

            match.append(context.getIndent()).append("{ $eq: [ \"$").append(innerField)
                    .append("\", \"$$").append(outerField).append("\" ] }");
            if (i < correlations.size() - 1) match.append(",");
            match.append("\n");
        }

        context.decreaseIndent();
        match.append(context.getIndent()).append("]\n");
        context.decreaseIndent();
        match.append(context.getIndent()).append("} }");

        return match.toString();
    }
}