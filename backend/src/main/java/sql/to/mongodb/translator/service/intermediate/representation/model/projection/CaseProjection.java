package sql.to.mongodb.translator.service.intermediate.representation.model.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.CaseExpression;

@Getter
@Setter
public class CaseProjection implements Projectionable {
    private CaseExpression expression;
    private String alias;
}
