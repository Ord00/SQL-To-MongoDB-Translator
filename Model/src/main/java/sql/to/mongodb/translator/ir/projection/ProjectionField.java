package sql.to.mongodb.translator.ir.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Field;

@Getter
@Setter
public class ProjectionField extends Field implements Projectionable {
    private String alias;
}
