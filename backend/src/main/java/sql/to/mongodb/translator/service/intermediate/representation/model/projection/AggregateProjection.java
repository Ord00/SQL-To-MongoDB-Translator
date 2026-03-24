package sql.to.mongodb.translator.service.intermediate.representation.model.projection;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregateProjection implements Projectionable {
    private AggregateType type;
    private ProjectionField field;
    private boolean distinct;
    private String alias;

    public enum AggregateType {
        COUNT, SUM, AVG, MIN, MAX
    }
}
