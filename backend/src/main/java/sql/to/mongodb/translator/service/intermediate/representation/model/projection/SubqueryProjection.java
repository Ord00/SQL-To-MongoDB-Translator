package sql.to.mongodb.translator.service.intermediate.representation.model.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.CorrelationSubquery;

@Getter
@Setter
public class SubqueryProjection extends CorrelationSubquery implements Projectionable {
    private String alias;
}
