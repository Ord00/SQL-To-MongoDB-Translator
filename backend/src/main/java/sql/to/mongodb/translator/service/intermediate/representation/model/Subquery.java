package sql.to.mongodb.translator.service.intermediate.representation.model;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

@Getter
@Setter
public class Subquery implements Expressionable {
    protected SqlToMongoIR subqueryIR;
}