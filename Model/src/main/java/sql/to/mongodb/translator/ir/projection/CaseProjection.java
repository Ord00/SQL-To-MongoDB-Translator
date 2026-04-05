package sql.to.mongodb.translator.ir.projection;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.expression.CaseExpression;

@Getter
@Setter
@EqualsAndHashCode
public class CaseProjection implements Projectionable {
    private CaseExpression expression;
    private String alias;
}
