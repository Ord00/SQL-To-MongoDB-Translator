package sql.to.mongodb.translator.ir.join;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = JoinTable.class, name = "table"),
        @JsonSubTypes.Type(value = JoinSubquery.class, name = "subquery"),
})
public interface Joinable {
    String getAlias();
    void setAlias(String alias);
}
