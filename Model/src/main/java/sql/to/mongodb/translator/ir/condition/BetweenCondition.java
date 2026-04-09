package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public class BetweenCondition extends OperandLeafNode {
    private BigDecimal start;
    private BigDecimal end;
}
