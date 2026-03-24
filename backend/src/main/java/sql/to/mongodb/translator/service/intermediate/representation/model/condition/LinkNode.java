package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LinkNode extends ConditionNode {

    public enum LinkType {
        AND, OR, NOT
    }

    private LinkType type;
    private List<ConditionNode> children = new ArrayList<>();
}
