package sql.to.mongodb.translator.ir.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.CorrelationSubquery;

@Getter
@Setter
public class SubqueryProjection extends CorrelationSubquery implements Projectionable {
    private String alias;
}
