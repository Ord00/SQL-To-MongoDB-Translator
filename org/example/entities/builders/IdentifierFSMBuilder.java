package org.example.entities.builders;

import org.example.entities.finite.automata.FSM;
import org.example.entities.finite.automata.FSMState;
import org.example.entities.finite.automata.FSMTransition;

import java.util.ArrayList;

public class IdentifierFSMBuilder extends AbstractFSMBuilder {

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

        FSM fsm = new FSM();
        fsm.sigma = sigma;

        var state0 = new FSMState( "0", true, false);
        fsm.states.add(state0);
        var stateEnd = new FSMState("1", false, true);
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
