package sql.to.mongodb.translator.entities.builders.special.words;

import sql.to.mongodb.translator.entities.finite.automata.FSM;

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
