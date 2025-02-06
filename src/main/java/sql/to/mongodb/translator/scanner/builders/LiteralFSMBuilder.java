package sql.to.mongodb.translator.scanner.builders;

import sql.to.mongodb.translator.scanner.finite.automata.FSM;
import sql.to.mongodb.translator.scanner.finite.automata.FSMState;
import sql.to.mongodb.translator.scanner.finite.automata.FSMTransition;

public class LiteralFSMBuilder extends FSMBuilder {

    @Override
    public FSM build() {

        FSM fsm = new FSM();

        FSMState stateStart = new FSMState("start", true, false);
        fsm.states.add(stateStart);
        FSMState stateInterm = new FSMState("interm", false,false);
        fsm.states.add(stateInterm);
        FSMState stateEnd = new FSMState("end", false, true);
        fsm.states.add(stateEnd);

        FSMTransition transitionStartInterm = new FSMTransition(stateStart, stateInterm, '\'');
        fsm.transitions.add(transitionStartInterm);
        FSMTransition transitionIntermEnd = new FSMTransition(stateInterm, stateEnd, '\'');
        fsm.transitions.add(transitionIntermEnd);

        fsm.transitions.add(new FSMTransition(stateInterm, stateInterm, null));

        return fsm;
    }
}
