package sql.to.mongodb.translator.service.intermediate.representation.processors;

import sql.to.mongodb.translator.service.intermediate.representation.model.Constant;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.CaseExpression;
import sql.to.mongodb.translator.service.intermediate.representation.model.expression.Expressionable;

import java.util.ArrayList;
import java.util.List;

public class CaseBuilder {

    private final List<CaseExpression.WhenThen> whenThenList = new ArrayList<>();
    private Expressionable elseExpression;

    private CaseBuilder() {}

    public static CaseBuilder create() {
        return new CaseBuilder();
    }

    public CaseBuilder when(Expressionable condition, Expressionable result) {
        whenThenList.add(new CaseExpression.WhenThen(condition, result));
        return this;
    }

    public CaseBuilder otherwise(Expressionable expression) {
        this.elseExpression = expression;
        return this;
    }

    public CaseBuilder otherwise(Constant constant) {
        this.elseExpression = constant;
        return this;
    }

    public CaseExpression build() {
        if (whenThenList.isEmpty()) {
            throw new IllegalStateException("CASE expression must have at least one WHEN clause");
        }

        CaseExpression caseExpr = new CaseExpression();
        caseExpr.getWhenThenList().addAll(whenThenList);
        caseExpr.setElseExpression(elseExpression);
        return caseExpr;
    }
}
