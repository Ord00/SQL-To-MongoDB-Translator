package org.example.entities.builders;

import org.example.entities.finite.automata.FSM;
import org.example.entities.finite.automata.FSMState;
import org.example.entities.finite.automata.FSMTransition;

public class AllFSMBuilder extends AbstractFSMBuilder {

    @Override
    public FSM build() {
        FSM fsm = new FSM();

        FSMState stateStart = new FSMState("start", true, false);
        fsm.states.add(stateStart);
        FSMState stateEnd = new FSMState("end", false, true);
        fsm.states.add(stateEnd);

        var transition = new FSMTransition(stateStart, stateEnd, '*');
        fsm.transitions.add(transition);

        return fsm;
    }
}
