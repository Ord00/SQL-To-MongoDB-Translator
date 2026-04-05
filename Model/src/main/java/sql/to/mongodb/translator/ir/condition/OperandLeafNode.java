package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public abstract class OperandLeafNode extends LeafNode {
    public Expressionable operand;
}
