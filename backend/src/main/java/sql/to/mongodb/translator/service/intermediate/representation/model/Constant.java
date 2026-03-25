package sql.to.mongodb.translator.service.intermediate.representation.model;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Arithmetical;

@Getter
@Setter
public class Constant implements Arithmetical {
    private ConstantType type;
    private Object value;

    public enum ConstantType {
        NUMBER,
        STRING,
        BOOLEAN,
        NULL
    }

    public static Constant ofNumber(String value) {
        Constant c = new Constant();
        c.type = ConstantType.NUMBER;
        c.value = Double.parseDouble(value);  // или BigDecimal
        return c;
    }

    public static Constant ofString(String value) {
        Constant c = new Constant();
        c.type = ConstantType.STRING;
        c.value = value;
        return c;
    }

    public static Constant ofNull() {
        Constant c = new Constant();
        c.type = ConstantType.NULL;
        c.value = null;
        return c;
    }

    @Override
    public String toString() {
        return switch (type) {
            case STRING -> "'" + value + "'";
            case NUMBER -> value.toString();
            case NULL -> "null";
            default -> value != null ? value.toString() : "null";
        };
    }
}
