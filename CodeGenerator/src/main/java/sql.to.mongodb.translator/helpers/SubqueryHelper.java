package sql.to.mongodb.translator.helpers;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.Subquery;

@Component
public class SubqueryHelper {

    public boolean isCorrelated(Subquery subquery) {
        if (subquery == null) return false;
        if (subquery instanceof CorrelationSubquery cs) {
            return cs.getCorrelations() != null && !cs.getCorrelations().isEmpty();
        }
        return false;
    }
}