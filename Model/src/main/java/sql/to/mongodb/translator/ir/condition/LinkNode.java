package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
public class LinkNode extends ConditionNode {

    public enum LinkType {
        AND, OR, NOT
    }

    private LinkType type;
    private List<ConditionNode> children = new ArrayList<>();
}
