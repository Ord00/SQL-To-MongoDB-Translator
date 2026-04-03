package sql.to.mongodb.translator.ir;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
public class Subquery implements Expressionable {
    protected SqlToMongoIR subqueryIR;
}