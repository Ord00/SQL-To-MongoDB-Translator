package sql.to.mongodb.translator.service.intermediate.representation.model.condition;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class InCondition extends LeafNode {
    private List<Expressionable> inValues = new ArrayList<>();
}
