package sql.to.mongodb.translator.service.intermediate.representation.model.join;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.Subquery;

@Getter
@Setter
public class JoinSubquery extends Subquery implements Joinable {
    private String alias;
}
