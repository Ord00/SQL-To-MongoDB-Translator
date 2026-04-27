package sql.to.mongodb.translator.ir.join;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class JoinTable implements Joinable {
    private String value;
    private String alias;

    @JsonCreator
    public JoinTable(@JsonProperty("value") String value,
                     @JsonProperty("alias") String alias) {
        this.value = value;
        this.alias = alias;
    }
}
