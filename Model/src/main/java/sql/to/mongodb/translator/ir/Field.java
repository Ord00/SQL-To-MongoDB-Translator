package sql.to.mongodb.translator.ir;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Arithmetical;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Field implements Arithmetical {

    protected String source;
    protected String field;

    public Field(String field) {
        this.field = field;
    }

    public boolean isAllFields() {
        return "*".equals(field);
    }

    @Override
    public String toString() {
        return source + '.' + field;
    }
}