package sql.to.mongodb.translator.service.code.generator.stages;

import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.helpers.FieldHelper;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.SortField;

import java.util.ArrayList;
import java.util.List;

public class SortStageGenerator extends BaseGenerator {

    public SortStageGenerator(SqlToMongoIR ir, GenerationContext context) {
        super(ir, context);
    }

    @Override
    public String generate() {
        if (ir.getOrderBy().isEmpty()) {
            return "";
        }

        StringBuilder sort = new StringBuilder(indent() + "{ $sort: { ");

        List<String> fields = new ArrayList<>();
        for (SortField sf : ir.getOrderBy()) {
            String field = FieldHelper.getFullFieldName(sf);
            int direction = sf.getDirection() == SortField.SortDirection.ASC ? 1 : -1;
            fields.add(field + ": " + direction);
        }

        sort.append(String.join(", ", fields));
        sort.append(" } }");

        return sort.toString();
    }

    public String generateFindSort() {
        if (ir.getOrderBy().isEmpty()) {
            return "";
        }

        StringBuilder sort = new StringBuilder("{ ");
        List<String> fields = new ArrayList<>();
        for (SortField sf : ir.getOrderBy()) {
            String field = FieldHelper.getFullFieldName(sf);
            int direction = sf.getDirection() == SortField.SortDirection.ASC ? 1 : -1;
            fields.add(field + ": " + direction);
        }
        sort.append(String.join(", ", fields));
        sort.append(" }");

        return sort.toString();
    }
}
