package sql.to.mongodb.translator.ir;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CorrelationSubquery extends Subquery {
    private List<CorrelationCondition> correlations = new ArrayList<>();

    public CorrelationSubquery(SqlToMongoIR sqlToMongoIR, List<CorrelationCondition> correlations) {
        super(sqlToMongoIR);
        this.correlations = correlations;
    }
}
