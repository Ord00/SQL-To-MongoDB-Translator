package sql.to.mongodb.translator.ir.condition;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LinkNode extends ConditionNode {

    public enum LinkType {
        AND, OR, NOT
    }

    private LinkType type;
    private List<ConditionNode> children = new ArrayList<>();
}
