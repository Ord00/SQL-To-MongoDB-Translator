package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

@Getter
@Setter
public abstract class OperandLeafNode extends LeafNode {
    private Expressionable operand;
}
