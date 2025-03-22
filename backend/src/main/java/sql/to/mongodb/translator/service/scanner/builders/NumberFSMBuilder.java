package sql.to.mongodb.translator.service.scanner.builders;

import sql.to.mongodb.translator.service.scanner.finite.automata.FSM;
import sql.to.mongodb.translator.service.scanner.finite.automata.FSMState;
import sql.to.mongodb.translator.service.scanner.finite.automata.FSMTransition;

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

        FSMState stateMinus = new FSMState("-", false, false);
        fsm.states.add(stateMinus);

        FSMState stateEnd = new FSMState("1", false, true);
        fsm.states.add(stateEnd);

        FSMTransition transToMinus = new FSMTransition(state0, stateMinus, '-');
        fsm.transitions.add(transToMinus);

        for (Character letter : sigma) {
            FSMTransition transition0 = new FSMTransition(state0, stateEnd, letter);
            fsm.transitions.add(transition0);

            FSMTransition transFromMinus = new FSMTransition(stateMinus, stateEnd, letter);
            fsm.transitions.add(transFromMinus);

            FSMTransition transitionEnd = new FSMTransition(stateEnd, stateEnd, letter);
            fsm.transitions.add(transitionEnd);
        }

        return fsm;
    }
}
