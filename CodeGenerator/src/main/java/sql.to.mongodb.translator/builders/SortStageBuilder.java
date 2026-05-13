package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.SortField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;

import java.util.stream.Collectors;

@Component
public class SortStageBuilder {

    public String build(SqlToMongoIR ir, GenerationContext context) {
        if (ir.getOrderBy().isEmpty()) {
            return null;
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $sort: " + sortToString(ir) + " }";
        } else {
            return sortToString(ir);
        }
    }

    public String buildFindSort(SqlToMongoIR ir) {
        if (ir.getOrderBy().isEmpty()) return null;
        return sortToString(ir);
    }

    private String sortToString(SqlToMongoIR ir) {
        return "{ " + ir.getOrderBy().stream()
                .map(s -> s.getFullField() + ": " +
                        (s.getDirection() == SortField.SortDirection.ASC ? 1 : -1))
                .collect(Collectors.joining(", ")) + " }";
    }
}
