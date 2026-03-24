package sql.to.mongodb.translator.service.intermediate.representation.model.expression;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CaseExpression implements Expressionable {

    private List<WhenThen> whenThenList = new ArrayList<>();
    private Expressionable elseExpression;  // else может быть любым выражением

    public void addWhenThen(Expressionable when, Expressionable then) {
        whenThenList.add(new WhenThen(when, then));
    }

    @Getter
    @Setter
    public static class WhenThen implements Expressionable {
        // Для формы CASE WHEN condition: используется condition
        private Expressionable condition;

        private Expressionable result;

        // Конструктор для формы с условием
        public WhenThen(Expressionable condition, Expressionable result) {
            this.condition = condition;
            this.result = result;
        }
    }
}