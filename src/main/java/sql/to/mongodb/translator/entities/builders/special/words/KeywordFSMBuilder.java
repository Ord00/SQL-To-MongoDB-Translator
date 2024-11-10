package sql.to.mongodb.translator.entities.builders.special.words;

import sql.to.mongodb.translator.entities.finite.automata.FSM;

import java.util.ArrayList;
import java.util.List;

public class KeywordFSMBuilder extends SpecialWordsFSMBuilder {

    @Override
    public FSM build() {

        words = new ArrayList<>(List.of(
                "SELECT",
                "DISTINCT",
                "FROM",
                "LEFT",
                "RIGHT",
                "OUTER",
                "INNER",
                "JOIN",
                "WHERE",
                "AND",
                "OR",
                "IN",
                "BETWEEN",
                "GROUP",
                "HAVING",
                "ORDER",
                "BY",
                "NULL"
        ));
        return super.build();
    }
}