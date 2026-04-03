package sql.to.mongodb.translator.ir.expression;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinaryOperation implements Arithmetical {
    private Arithmetical left;
    private Arithmetical right;
    private Operator operator;

    public BinaryOperation(Arithmetical left,
                           Arithmetical right,
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
