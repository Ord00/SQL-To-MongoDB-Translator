package sql.to.mongodb.translator.service.intermediate.representation.model.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.Field;

@Getter
@Setter
public class ProjectionField extends Field implements Projectionable {
    private String alias;
}
