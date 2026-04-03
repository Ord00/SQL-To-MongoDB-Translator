package sql.to.mongodb.translator.ir.join;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinTable implements Joinable {
    private String value;
    private String alias;
}
