package sql.to.mongodb.translator.ir.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.CorrelationSubquery;

@Getter
@Setter
public class ExistsCondition extends LeafNode {
    private boolean isExists;
    private CorrelationSubquery subquery;
}
