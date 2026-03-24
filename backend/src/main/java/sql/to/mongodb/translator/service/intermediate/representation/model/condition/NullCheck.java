package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

@Getter
@Setter
public class NullCheck extends LeafNode {
    private Expressionable operand;
    private boolean isNull;
}
