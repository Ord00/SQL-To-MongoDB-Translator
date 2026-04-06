package sql.to.mongodb.translator.ir;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;
import sql.to.mongodb.translator.ir.projection.ProjectionField;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Aggregate implements Expressionable {
    protected AggregateType type;
    protected ProjectionField field;
    protected boolean distinct;

    public enum AggregateType {
        COUNT, SUM, AVG, MIN, MAX
    }
}
