package sql.to.mongodb.translator.ir.condition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = LinkNode.class, name = "link"),
        @JsonSubTypes.Type(value = LeafNode.class, name = "leaf"),
        @JsonSubTypes.Type(value = Comparison.class, name = "comparison"),
        @JsonSubTypes.Type(value = BetweenCondition.class, name = "between"),
        @JsonSubTypes.Type(value = InCondition.class, name = "in"),
        @JsonSubTypes.Type(value = NullCheck.class, name = "null_check"),
        @JsonSubTypes.Type(value = ExistsCondition.class, name = "exists")
})
public abstract class ConditionNode {
}
