package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BetweenCondition extends OperandLeafNode {
    private BigDecimal start;
    private BigDecimal end;
}
