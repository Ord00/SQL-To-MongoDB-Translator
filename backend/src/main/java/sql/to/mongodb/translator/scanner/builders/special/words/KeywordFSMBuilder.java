package sql.to.mongodb.translator.scanner.builders.special.words;

import sql.to.mongodb.translator.scanner.finite.automata.FSM;

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
                "USING",
                "AS",
                "ASC",
                "DESC",
                "LIMIT",
                "OFFSET",
                "CASE",
                "WHEN",
                "THEN",
                "ELSE",
                "END"
        ));
        return super.build();
    }
}