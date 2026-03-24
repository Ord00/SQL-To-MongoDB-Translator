package sql.to.mongodb.translator.service.intermediate.representation.model.expression;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UnaryOperation extends ArithmeticExpression {
    private ArithmeticExpression operand;
    private UnaryOperator operator;

    public UnaryOperation(ArithmeticExpression operand,
                          UnaryOperator unaryOperator) {
        this.operand = operand;
        this.operator = unaryOperator;
    }

    public enum UnaryOperator {
        NEGATE  // -x
    }
}
