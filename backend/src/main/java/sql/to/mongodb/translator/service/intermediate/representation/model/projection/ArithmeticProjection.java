package sql.to.mongodb.translator.service.intermediate.representation.model.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Arithmetical;

@Getter
@Setter
public class ArithmeticProjection implements Projectionable {
    private Arithmetical expression;
    private String alias;
}
