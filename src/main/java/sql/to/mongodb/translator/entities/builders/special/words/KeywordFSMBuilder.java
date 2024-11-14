package sql.to.mongodb.translator.entities.builders.special.words;

import sql.to.mongodb.translator.entities.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class KeywordFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "DISTINCT",
                "FROM",
                "LEFT",
                "RIGHT",
                "OUTER",
                "INNER",
                "JOIN",
                "WHERE",
                "GROUP",
                "HAVING",
                "ORDER",
                "BY",
                "ON",
                "AS"
        ));
        return super.build();
    }
}