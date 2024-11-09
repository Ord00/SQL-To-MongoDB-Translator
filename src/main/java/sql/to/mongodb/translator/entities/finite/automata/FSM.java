package sql.to.mongodb.translator.entities.finite.automata;

import java.util.ArrayList;
import java.util.List;

public class FSM {

    public List<Character> sigma;

    public List<FSMState> states;

    public List<FSMTransition> transitions;

    public FSM() {
        states = new ArrayList<>();
        transitions = new ArrayList<>();
    }

    public Boolean simulate(String pattern) {

        FSMState currentState = states.stream()
                .filter(i -> i.isStart)
                .findFirst()
                .orElse(null);

        if (currentState == null) {
            return false;
        }

        char[] lowerPattern = pattern.toLowerCase().toCharArray();

        for (Character c : lowerPattern) {

            String curId = currentState.id;
            List<FSMTransition> transFromCurState = transitions.stream()
                    .filter(i -> i.start.id.equals(curId))
                    .toList();

            for (FSMTransition transition : transFromCurState) {
                currentState = transition.transit(c);
                if (currentState != null) {
                    break;
                }
            }

            if (transFromCurState.isEmpty() || currentState == null) {
                return false;
            }
        }

        return currentState.isEnd;
    }
}
