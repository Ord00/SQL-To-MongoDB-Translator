package sql.to.mongodb.translator.service.intermediate.representation.details;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectionField {

    private String source;
    private String field;
    private String alias;

    public boolean isAllFields() {
        return "*".equals(field);
    }
}
