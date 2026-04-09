package sql.to.mongodb.translator.ir;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class GroupByField extends Field {

    public GroupByField(String source, String field) {
        super(source, field);
    }

    // Alias
    public GroupByField(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return source != null ? source + "." + field : field;
    }
}
