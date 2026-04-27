package sql.to.mongodb.translator.ir.join;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.condition.ConditionNode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JoinInfo {

    public enum JoinType { INNER, LEFT, RIGHT, FULL, CROSS }

    private JoinType type = JoinType.INNER;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private Joinable left;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private Joinable right;
    private ConditionNode joinCondition;

    public JoinInfo(Joinable left) {
        this.left = left;
    }
}
