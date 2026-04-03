package sql.to.mongodb.translator.ir;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CorrelationSubquery extends Subquery {
    private List<CorrelationCondition> correlations = new ArrayList<>();
}
