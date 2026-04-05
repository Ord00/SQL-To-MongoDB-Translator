package sql.to.mongodb.translator.ir.expression;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BinaryOperation implements Arithmetical {
    private Arithmetical left;
    private Arithmetical right;
    private Operator operator;

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
