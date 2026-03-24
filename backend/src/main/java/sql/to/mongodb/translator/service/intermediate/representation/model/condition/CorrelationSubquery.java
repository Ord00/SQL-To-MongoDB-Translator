package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.CorrelationCondition;
import sql.to.mongodb.translator.service.intermediate.representation.model.Subquery;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CorrelationSubquery extends Subquery {
    private List<CorrelationCondition> correlations = new ArrayList<>();
}
