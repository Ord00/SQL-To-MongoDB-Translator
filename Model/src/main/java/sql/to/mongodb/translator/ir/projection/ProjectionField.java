package sql.to.mongodb.translator.ir.projection;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Field;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ProjectionField extends Field implements Projectionable {
    private String alias;

    public ProjectionField(String source, String field, String alias) {
        this.source = source;
        this.field = field;
        this.alias = alias;
    }
}
