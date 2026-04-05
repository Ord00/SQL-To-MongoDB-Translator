package sql.to.mongodb.translator.ir.projection;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.CorrelationSubquery;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public class SubqueryProjection extends CorrelationSubquery implements Projectionable {
    private String alias;
}
