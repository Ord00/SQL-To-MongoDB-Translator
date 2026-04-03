package sql.to.mongodb.translator.ir;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Arithmetical;

@Getter
@Setter
public class Field implements Arithmetical {

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