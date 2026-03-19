package sql.to.mongodb.translator.service.code.generator.helpers;

import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo;

import java.util.List;
import java.util.stream.Collectors;

public class SubqueryHelper {

    public static boolean isCorrelated(SubqueryInfo subquery) {
        return subquery.getCorrelations() != null && !subquery.getCorrelations().isEmpty();
    }

    public static String buildLetVariables(List<CorrelationCondition> correlations) {
        return correlations.stream()
                .map(c -> {
                    String outerField = c.getOuterField().split("\\.")[1];
                    return outerField + ": \"$" + outerField + "\"";
                })
                .collect(Collectors.joining(", "));
    }

    public static String getSubqueryCollection(SqlToMongoIR subIR) {
        return FormatHelper.escapeIdentifier(subIR.getMainCollection());
    }

    public static boolean isScalarSubquery(SubqueryInfo subquery) {
        return subquery.getType() == SubqueryInfo.SubqueryType.SCALAR;
    }
}
