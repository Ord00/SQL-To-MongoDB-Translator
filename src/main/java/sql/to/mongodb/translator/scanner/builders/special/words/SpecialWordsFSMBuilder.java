package sql.to.mongodb.translator.scanner.builders.special.words;

import sql.to.mongodb.translator.scanner.builders.FSMBuilder;
import sql.to.mongodb.translator.scanner.finite.automata.FSM;
import sql.to.mongodb.translator.scanner.finite.automata.FSMState;
import sql.to.mongodb.translator.scanner.finite.automata.FSMTransition;

import java.util.List;

public abstract class SpecialWordsFSMBuilder extends FSMBuilder {

    protected static List<String> words;

    @Override
    public FSM build() {

        var fsm = new FSM();

        FSMState state0 = new FSMState("0", true, false);
        fsm.states.add(state0);

        words.stream()
                .map(String::toLowerCase)
                .forEach(word -> {
                    FSMState currentState = state0;
                    int length = word.length();
                    int index = 1;
                    for (int i = 0; i < length; i++) {

                        boolean isEnd = i == length - 1;
                        FSMState startState = currentState;
                        int pos = i;

                        FSMState nextState = fsm.transitions.stream()
                                .filter(k -> k.start.equals(startState) && k.item.equals(word.charAt(pos)))
                                .map(k -> k.end)
                                .findFirst()
                                .orElse(null);

                        if (nextState != null) {
                            nextState.isEnd = isEnd;
                            currentState = nextState;
                        } else {
                            FSMState state = new FSMState(String.format("%s%d", word, index), false, isEnd);
                            fsm.states.add(state);

                            FSMTransition transition = new FSMTransition(currentState, state, word.charAt(i));
                            fsm.transitions.add(transition);

                            currentState = state;
                        }
                        index++;
                    }
                });

        return fsm;
    }
}
