package sql.to.mongodb.translator.ir.expression;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class UnaryOperation implements Arithmetical {
    private Arithmetical operand;
    private UnaryOperator operator;

    public UnaryOperation(Arithmetical operand,
                          UnaryOperator unaryOperator) {
        this.operand = operand;
        this.operator = unaryOperator;
    }

    public enum UnaryOperator {
        NEGATE  // -x
    }
}
