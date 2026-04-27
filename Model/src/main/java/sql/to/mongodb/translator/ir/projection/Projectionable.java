package sql.to.mongodb.translator.ir.projection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProjectionField.class, name = "field"),
        @JsonSubTypes.Type(value = AggregateProjection.class, name = "aggregate"),
        @JsonSubTypes.Type(value = ArithmeticProjection.class, name = "arithmetic"),
        @JsonSubTypes.Type(value = CaseProjection.class, name = "case"),
        @JsonSubTypes.Type(value = SubqueryProjection.class, name = "subquery")
})
public interface Projectionable {
    String getAlias();
    void setAlias(String alias);
}