package sql.to.mongodb.translator.ir.expression;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import sql.to.mongodb.translator.ir.Aggregate;
import sql.to.mongodb.translator.ir.Constant;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.GroupByField;
import sql.to.mongodb.translator.ir.SortField;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.projection.ProjectionField;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CaseExpression.class, name = "case"),
        @JsonSubTypes.Type(value = CaseExpression.WhenThen.class, name = "when"),
        @JsonSubTypes.Type(value = Subquery.class, name = "subquery"),
        @JsonSubTypes.Type(value = Aggregate.class, name = "aggregate"),
        @JsonSubTypes.Type(value = BinaryOperation.class, name = "binary"),
        @JsonSubTypes.Type(value = Constant.class, name = "constant"),
        @JsonSubTypes.Type(value = Field.class, name = "field"),
        @JsonSubTypes.Type(value = GroupByField.class, name = "group"),
        @JsonSubTypes.Type(value = ProjectionField.class, name = "projection"),
        @JsonSubTypes.Type(value = SortField.class, name = "sort"),
        @JsonSubTypes.Type(value = UnaryOperation.class, name = "unary")
})
public interface Expressionable {
}
