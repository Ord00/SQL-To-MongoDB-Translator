package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.CorrelationSubquery;

@Getter
@Setter
public class ExistsCondition extends LeafNode {
    private boolean isExists;
    private CorrelationSubquery subquery;
}
