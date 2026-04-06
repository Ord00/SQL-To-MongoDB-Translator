package sql.to.mongodb.translator.ir.projection;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Aggregate;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class AggregateProjection extends Aggregate implements Projectionable {
    private String alias;

    public AggregateProjection(AggregateType aggregateType,
                               ProjectionField field,
                               boolean distinct,
                               String alias) {
        this.type = aggregateType;
        this.field = field;
        this.distinct = distinct;
        this.alias = alias;
    }
}
