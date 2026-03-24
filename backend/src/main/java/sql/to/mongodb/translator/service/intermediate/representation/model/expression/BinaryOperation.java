package sql.to.mongodb.translator.service.intermediate.representation.model.expression;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class BinaryOperation extends ArithmeticExpression {
    private ArithmeticExpression left;
    private ArithmeticExpression right;
    private Operator operator;

    public BinaryOperation(ArithmeticExpression left,
                           ArithmeticExpression right,
                           Operator operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public enum Operator {
        ADD("$add"),
        SUBTRACT("$subtract"),
        MULTIPLY("$multiply"),
        DIVIDE("$divide"),
        MOD("$mod");

        private final String mongoOperator;

        Operator(String mongoOperator) {
            this.mongoOperator = mongoOperator;
        }
    }
}
