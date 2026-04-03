package sql.to.mongodb.translator.ir.condition;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NullCheck extends OperandLeafNode {
    private boolean isNull;
}
