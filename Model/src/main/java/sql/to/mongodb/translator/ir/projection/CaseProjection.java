package sql.to.mongodb.translator.ir.projection;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.CaseExpression;

@Getter
@Setter
public class CaseProjection implements Projectionable {
    private CaseExpression expression;
    private String alias;
}
