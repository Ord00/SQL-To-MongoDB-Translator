package sql.to.mongodb.translator.service.code.generator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.model.SortField;

import java.util.stream.Collectors;

@Component
public class SortStageBuilder {

    public String build(SqlToMongoIR ir, GenerationContext context) {
        if (ir.getOrderBy().isEmpty()) {
            return null;
        }

        if (context.isUseAggregationSyntax()) {
            return indent(context) + "{ $sort: " + sortToString(ir) + " }";
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

    private String indent(GenerationContext context) {
        return "  ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
