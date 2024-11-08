package org.example.entities.builders;

import org.example.entities.finite.automata.FSM;
import org.example.entities.finite.automata.FSMState;
import org.example.entities.finite.automata.FSMTransition;

import java.util.ArrayList;

public class LiteralFSMBuilder extends AbstractFSMBuilder {

    @Override
    public FSM build() {
        var sigma = new ArrayList<Character>();

        for (char c = 'A'; c <= 'Z'; c++) {
            sigma.add(c);
        }

        for (char c = 'a'; c <= 'z'; c++) {
            sigma.add(c);
        }

        sigma.add('%');
        sigma.add('_');

        var fsm = new FSM();
        fsm.sigma = sigma;

        FSMState stateStart = new FSMState("start", true, false);
        fsm.states.add(stateStart);
        FSMState stateInterm = new FSMState("interm", false,false);
        fsm.states.add(stateInterm);
        FSMState stateEnd = new FSMState("end", false, true);
        fsm.states.add(stateEnd);

        for (Character letter : sigma) {
            FSMTransition transition = new FSMTransition(stateInterm, stateInterm, letter);
            fsm.transitions.add(transition);
        }

        FSMTransition transitionStartInterm = new FSMTransition(stateStart, stateInterm, '\'');
        fsm.transitions.add(transitionStartInterm);
        FSMTransition transitionIntermEnd = new FSMTransition(stateInterm, stateEnd, '\'');
        fsm.transitions.add(transitionIntermEnd);

        return fsm;
    }
}
