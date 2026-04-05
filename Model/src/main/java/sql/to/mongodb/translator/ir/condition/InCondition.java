package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public class InCondition extends OperandLeafNode {
    private List<Expressionable> inValues = new ArrayList<>();
}
