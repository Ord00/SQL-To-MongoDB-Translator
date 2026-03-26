package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

import java.math.BigDecimal;

@Getter
@Setter
public class BetweenCondition extends LeafNode {
    private Expressionable operand;
    private BigDecimal start;
    private BigDecimal end;
}
