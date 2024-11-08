package sql.to.mongodb.translator.entities.builders;

import sql.to.mongodb.translator.entities.finite.automata.FSM;
import sql.to.mongodb.translator.entities.finite.automata.FSMState;
import sql.to.mongodb.translator.entities.finite.automata.FSMTransition;

import java.util.ArrayList;
import java.util.List;

public class KeywordFSMBuilder extends AbstractFSMBuilder {

    @Override
    public FSM build() {
        ArrayList<String> keywords = new ArrayList<>(List.of(
                "SELECT",
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
                "BETWEEN"
        ));

        var fsm = new FSM();

        FSMState state0 = new FSMState("0", true, false);
        fsm.states.add(state0);

        keywords.stream()
                .map(String::toLowerCase)
                .forEach(keyword -> {
                    FSMState currentState = state0;
                    int l = keyword.length();
                    int index = 1;
                    for (int i = 0; i < l; i++) {
                        boolean end = i == l - 1;

                        FSMState searchState = currentState;
                        int pos = i;
                        FSMTransition searchTrans = fsm.transitions.stream()
                                .filter(k -> k.equals(new FSMTransition(searchState, null, keyword.charAt(pos))))
                                .findFirst()
                                .orElse(null);

                        if (searchTrans != null) {
                            searchTrans.end.isEnd = end;
                            currentState = searchTrans.end;
                        } else {
                            FSMState state = new FSMState(String.format("%s%d", keyword, index), false, end);
                            fsm.states.add(state);

                            FSMTransition transition = new FSMTransition(currentState, state, keyword.charAt(i));
                            fsm.transitions.add(transition);

                            currentState = state;
                        }

                        index++;
                    }
                });

        return fsm;
    }
}
