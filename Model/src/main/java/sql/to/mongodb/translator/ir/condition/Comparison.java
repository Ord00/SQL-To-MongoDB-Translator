package sql.to.mongodb.translator.ir.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

@Getter
@Setter
public class Comparison extends OperandLeafNode {
    private Expressionable value;
    private String operator;
}
