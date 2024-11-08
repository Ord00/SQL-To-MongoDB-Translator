package org.example.entities.builders;

import org.example.entities.finite.automata.FSM;
import org.example.entities.finite.automata.FSMState;
import org.example.entities.finite.automata.FSMTransition;

import java.util.ArrayList;

public class NumberFSMBuilder extends AbstractFSMBuilder {

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
        }

        return fsm;
    }
}
