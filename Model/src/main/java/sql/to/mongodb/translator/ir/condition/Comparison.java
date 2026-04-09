package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Comparison extends OperandLeafNode {
    private String operator;
    private Expressionable value;

    public Comparison(Expressionable operand, String operator, Expressionable value) {
        this.operand = operand;
        this.operator = operator;
        this.value = value;
    }
}
