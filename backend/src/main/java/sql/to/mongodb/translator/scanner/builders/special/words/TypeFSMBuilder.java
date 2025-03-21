package sql.to.mongodb.translator.scanner.builders.special.words;

import sql.to.mongodb.translator.scanner.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class TypeFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "VARCHAR",
                "CHAR",
                "DECIMAL",
                "TEXT",
                "DATE",
                "INT",
                "FLOAT"
        ));
        return super.build();
    }
}
