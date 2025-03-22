package sql.to.mongodb.translator.service.scanner.builders;

import sql.to.mongodb.translator.service.scanner.finite.automata.FSM;
import sql.to.mongodb.translator.service.scanner.finite.automata.FSMState;
import sql.to.mongodb.translator.service.scanner.finite.automata.FSMTransition;

import java.util.ArrayList;

public class IdentifierFSMBuilder extends FSMBuilder {

    @Override
    public FSM build() {

        var sigma = new ArrayList<Character>();

        for (char c = 'A'; c <= 'Z'; c++) {
            sigma.add(c);
        }

        for (char c = 'a'; c <= 'z'; c++) {
            sigma.add(c);
        }

        sigma.add('_');

        for (char c = '0'; c <= '9'; c++) {
            sigma.add(c);
        }

        FSM fsm = new FSM();
        fsm.sigma = sigma;

        var state0 = new FSMState( "0", true, false);
        fsm.states.add(state0);
        var stateEnd = new FSMState("1", false, true);
        fsm.states.add(stateEnd);

        for (Character letter : sigma) {

            if (letter < '0' || letter > '9') {
                FSMTransition transition0 = new FSMTransition(state0, stateEnd, letter);
                fsm.transitions.add(transition0);
            }
            FSMTransition transitionEnd = new FSMTransition(stateEnd, stateEnd, letter);
            fsm.transitions.add(transitionEnd);
        }

        return fsm;
    }
}
