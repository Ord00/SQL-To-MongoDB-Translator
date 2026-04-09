package sql.to.mongodb.translator.builders.special.words;

import sql.to.mongodb.translator.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class DclFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "GRANT",
                "REVOKE"
        ));
        return super.build();
    }
}
