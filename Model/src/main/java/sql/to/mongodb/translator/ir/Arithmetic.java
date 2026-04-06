package sql.to.mongodb.translator.ir;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Arithmetical;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Arithmetic implements Expressionable {
    protected Arithmetical expression;
}
