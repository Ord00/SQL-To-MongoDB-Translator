package sql.to.mongodb.translator.scanner.builders.special.words;

import sql.to.mongodb.translator.scanner.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class LogicalExpressionFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "IN",
                "BETWEEN",
                "IS",
                "LIKE",
                "EXISTS",
                "ALL",
                "SOME",
                "ANY"
        ));
        return super.build();
    }
}
