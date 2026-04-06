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
public class NullCheck extends OperandLeafNode {
    private boolean isNull;

    public NullCheck(Expressionable operand, boolean isNull) {
        this.operand = operand;
        this.isNull = isNull;
    }
}
