package sql.to.mongodb.translator.ir.projection;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Arithmetic;
import sql.to.mongodb.translator.ir.expression.Arithmetical;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ArithmeticProjection extends Arithmetic implements Projectionable {
    private String alias;

    public ArithmeticProjection(Arithmetical expression, String alias) {
        this.expression = expression;
        this.alias = alias;
    }
}
