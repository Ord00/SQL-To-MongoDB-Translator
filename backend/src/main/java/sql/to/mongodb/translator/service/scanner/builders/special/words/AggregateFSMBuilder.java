package sql.to.mongodb.translator.service.scanner.builders.special.words;

import sql.to.mongodb.translator.service.scanner.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class AggregateFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "SUM",
                "COUNT",
                "MAX",
                "MIN",
                "AVG"
        ));
        return super.build();
    }
}
