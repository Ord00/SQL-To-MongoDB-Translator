package sql.to.mongodb.translator.service.intermediate.representation.model.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.Subquery;

@Getter
@Setter
public class SubqueryProjection extends Subquery implements Projectionable {
    private String alias;
}
