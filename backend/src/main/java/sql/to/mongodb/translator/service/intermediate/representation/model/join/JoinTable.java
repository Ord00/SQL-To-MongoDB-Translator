package sql.to.mongodb.translator.service.intermediate.representation.model.join;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinTable implements Joinable {
    private String value;
    private String alias;
}
