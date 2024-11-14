package sql.to.mongodb.translator.entities.builders.special.words;

import sql.to.mongodb.translator.entities.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class DdlFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "CREATE",
                "DROP",
                "ALTER",
                "TRUNCATE"
        ));
        return super.build();
    }
}
