package sql.to.mongodb.translator.service.intermediate.representation.details;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinInfo {
    public enum JoinType { INNER, LEFT, RIGHT, FULL, CROSS }

    private JoinType type = JoinType.INNER;
    private String leftTable;
    private String rightTable;
    private String leftAlias;
    private String rightAlias;
    private ConditionNode joinCondition;
}
