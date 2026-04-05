package sql.to.mongodb.translator.ir.projection;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Arithmetical;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ArithmeticProjection implements Projectionable {
    private Arithmetical expression;
    private String alias;
}
