package sql.to.mongodb.translator.ir.join;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Subquery;

@Getter
@Setter
public class JoinSubquery extends Subquery implements Joinable {
    private String alias;
}
