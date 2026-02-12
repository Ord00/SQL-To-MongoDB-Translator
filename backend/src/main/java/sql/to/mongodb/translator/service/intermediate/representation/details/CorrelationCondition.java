package sql.to.mongodb.translator.service.intermediate.representation.details;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CorrelationCondition {

    private String outerField;

    private String innerField;

    private String operator = "=";

    private CorrelationType correlationType = CorrelationType.EQUALITY;

    public enum CorrelationType {
        EQUALITY,
        INEQUALITY,
        COMPARISON,
        RANGE
    }

    public CorrelationCondition() {}

    public CorrelationCondition(String outerField, String innerField) {
        this.outerField = outerField;
        this.innerField = innerField;
    }

    public CorrelationCondition(String outerField, String innerField, String operator) {
        this.outerField = outerField;
        this.innerField = innerField;
        this.operator = operator;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", outerField, operator, innerField);
    }
}