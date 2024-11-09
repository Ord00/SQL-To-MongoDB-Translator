package sql.to.mongodb.translator.entities.builders;

import sql.to.mongodb.translator.entities.finite.automata.FSM;
import sql.to.mongodb.translator.entities.finite.automata.FSMState;
import sql.to.mongodb.translator.entities.finite.automata.FSMTransition;

import java.util.ArrayList;

public class NumberFSMBuilder extends FSMBuilder {

    @Override
    public FSM build() {

        var sigma = new ArrayList<Character>();

        for (char c = '0'; c <= '9'; c++) {
            sigma.add(c);
        }

        var fsm = new FSM();
        fsm.sigma = sigma;

        FSMState state0 = new FSMState("0", true, false);
        fsm.states.add(state0);
        FSMState stateEnd = new FSMState("1", false, true);
        fsm.states.add(stateEnd);

        for (Character letter : sigma) {
            FSMTransition transition0 = new FSMTransition(state0, stateEnd, letter);
            fsm.transitions.add(transition0);
            FSMTransition transitionEnd = new FSMTransition(stateEnd, stateEnd, letter);
            fsm.transitions.add(transitionEnd);
        }

        return fsm;
    }
}
