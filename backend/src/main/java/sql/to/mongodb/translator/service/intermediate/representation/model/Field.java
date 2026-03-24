package sql.to.mongodb.translator.service.intermediate.representation.model;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

@Getter
@Setter
public class Field implements Expressionable {

    protected String source;
    protected String field;

    public boolean isAllFields() {
        return "*".equals(field);
    }

    @Override
    public String toString() {
        return source + '.' + field;
    }
}