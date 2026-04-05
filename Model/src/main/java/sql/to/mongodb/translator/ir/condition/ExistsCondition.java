package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.CorrelationSubquery;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public class ExistsCondition extends LeafNode {
    private boolean isExists;
    private CorrelationSubquery subquery;
}
