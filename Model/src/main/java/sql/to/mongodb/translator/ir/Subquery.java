package sql.to.mongodb.translator.ir;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Subquery implements Expressionable {
    protected SqlToMongoIR subqueryIR;
}