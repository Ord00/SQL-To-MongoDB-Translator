package sql.to.mongodb.translator.builders.special.words;

import sql.to.mongodb.translator.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class TclFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "COMMIT",
                "ROLLBACK",
                "SAVEPOINT"
        ));
        return super.build();
    }
}
