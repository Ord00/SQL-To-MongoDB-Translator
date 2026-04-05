package sql.to.mongodb.translator.ir;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
@EqualsAndHashCode
public class Subquery implements Expressionable {
    protected SqlToMongoIR subqueryIR;
}