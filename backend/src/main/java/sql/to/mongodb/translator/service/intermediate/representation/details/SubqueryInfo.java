package sql.to.mongodb.translator.service.intermediate.representation.details;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SubqueryInfo {

    public enum SubqueryType { EXISTS, NOT_EXISTS, IN, SCALAR, COMPARISON }

    private SubqueryType type;
    private SqlToMongoIR subqueryIR;
    private List<CorrelationCondition> correlations = new ArrayList<>();
}
