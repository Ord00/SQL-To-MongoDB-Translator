package sql.to.mongodb.translator.entities.builders;

import sql.to.mongodb.translator.entities.finite.automata.FSM;
import sql.to.mongodb.translator.entities.finite.automata.FSMState;
import sql.to.mongodb.translator.entities.finite.automata.FSMTransition;

public class AllFSMBuilder extends FSMBuilder {

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
