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
                "BETWEEN"
        ));

        var fsm = new FSM();

        FSMState state0 = new FSMState("0", true, false);
        fsm.states.add(state0);

        keywords.stream()
                .map(String::toLowerCase)
                .forEach(keyword -> {
                    FSMState currentState = state0;
                    int length = keyword.length();
                    int index = 1;
                    for (int i = 0; i < length; i++) {

                        boolean isEnd = i == length - 1;
                        FSMState startState = currentState;
                        int pos = i;

                        FSMState nextState = fsm.transitions.stream()
                                .filter(k -> k.start.equals(startState) && k.item.equals(keyword.charAt(pos)))
                                .map(k -> k.end)
                                .findFirst()
                                .orElse(null);

                        if (nextState != null) {
                            nextState.isEnd = isEnd;
                            currentState = nextState;
                        } else {
                            FSMState state = new FSMState(String.format("%s%d", keyword, index), false, isEnd);
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
