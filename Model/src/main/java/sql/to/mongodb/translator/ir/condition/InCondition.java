package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.Expressionable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InCondition extends OperandLeafNode {
    private List<Expressionable> inValues = new ArrayList<>();

    public InCondition(Expressionable operand, List<Expressionable> inValues) {
        this.operand = operand;
        this.inValues = inValues;
    }
}
