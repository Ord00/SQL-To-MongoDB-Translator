package sql.to.mongodb.translator.ir.condition;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.CorrelationSubquery;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ExistsCondition extends LeafNode {
    private boolean isExists;
    private CorrelationSubquery subquery;
}
