package sql.to.mongodb.translator.ir.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Arithmetical;

@Getter
@Setter
public class ArithmeticProjection implements Projectionable {
    private Arithmetical expression;
    private String alias;
}
