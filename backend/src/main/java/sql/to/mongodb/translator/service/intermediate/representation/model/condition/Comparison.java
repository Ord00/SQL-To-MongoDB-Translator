package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

@Getter
@Setter
public class Comparison extends LeafNode {
    private Expressionable field;
    private Expressionable value;
    private String operator;
}
