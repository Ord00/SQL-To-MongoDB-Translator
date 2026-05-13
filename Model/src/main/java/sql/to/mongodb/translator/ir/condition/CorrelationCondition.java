package sql.to.mongodb.translator.ir.condition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sql.to.mongodb.translator.ir.Field;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class CorrelationCondition {

    private Field outerField;

    private Field innerField;

    private String operator;

    private CorrelationType correlationType = CorrelationType.EQUALITY;

    public enum CorrelationType {
        EQUALITY,
        INEQUALITY,
        COMPARISON,
        RANGE
    }

    public CorrelationCondition(Field outerField, Field innerField, String operator) {
        this.outerField = outerField;
        this.innerField = innerField;
        this.operator = operator;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", outerField, operator, innerField);
    }
}