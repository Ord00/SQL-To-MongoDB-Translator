package sql.to.mongodb.translator.service.intermediate.representation.details;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConditionNode {

    public enum ConditionType {
        SIMPLE, AND, OR, NOT,
        EXISTS, NOT_EXISTS,
        COMPARISON, IS_NULL, IS_NOT_NULL,
        BETWEEN, IN
    }

    private ConditionType type;
    private String field;
    private Object value;
    private String operator;
    private List<ConditionNode> children = new ArrayList<>();
}
