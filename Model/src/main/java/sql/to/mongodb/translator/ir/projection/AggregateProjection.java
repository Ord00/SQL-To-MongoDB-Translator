package sql.to.mongodb.translator.ir.projection;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class AggregateProjection implements Projectionable {
    private AggregateType type;
    private ProjectionField field;
    private boolean distinct;
    private String alias;

    public enum AggregateType {
        COUNT, SUM, AVG, MIN, MAX
    }
}
