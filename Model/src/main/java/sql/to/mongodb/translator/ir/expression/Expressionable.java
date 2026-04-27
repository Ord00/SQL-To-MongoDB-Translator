package sql.to.mongodb.translator.ir.expression;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import sql.to.mongodb.translator.ir.Field;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Field.class, name = "field"),
        @JsonSubTypes.Type(value = BinaryOperation.class, name = "binary"),
        @JsonSubTypes.Type(value = UnaryOperation.class, name = "unary"),
        @JsonSubTypes.Type(value = CaseExpression.class, name = "case")
})
public interface Expressionable {
}
