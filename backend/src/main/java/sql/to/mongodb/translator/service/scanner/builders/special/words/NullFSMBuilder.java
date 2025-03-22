package sql.to.mongodb.translator.service.scanner.builders.special.words;

import sql.to.mongodb.translator.service.scanner.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class NullFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "NULL"
        ));
        return super.build();
    }
}
