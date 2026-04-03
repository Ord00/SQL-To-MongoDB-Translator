package sql.to.mongodb.translator.service.intermediate.representation.model.join;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.condition.ConditionNode;

@Getter
@Setter
public class JoinInfo {

    public enum JoinType { INNER, LEFT, RIGHT, FULL, CROSS }

    private JoinType type = JoinType.INNER;
    private Joinable left;
    private Joinable right;
    private ConditionNode joinCondition;

    public JoinInfo() {}

    public JoinInfo(Joinable left) {
        this.left = left;
    }
}
