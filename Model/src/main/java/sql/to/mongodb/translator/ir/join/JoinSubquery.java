package sql.to.mongodb.translator.ir.join;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Subquery;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class JoinSubquery extends Subquery implements Joinable {
    private String alias;
}
