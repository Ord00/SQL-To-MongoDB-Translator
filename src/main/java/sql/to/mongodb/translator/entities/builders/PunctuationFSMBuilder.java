package sql.to.mongodb.translator.entities.builders;

import sql.to.mongodb.translator.entities.finite.automata.FSM;
import sql.to.mongodb.translator.entities.finite.automata.FSMState;
import sql.to.mongodb.translator.entities.finite.automata.FSMTransition;

import java.util.ArrayList;
import java.util.List;

public class PunctuationFSMBuilder extends AbstractFSMBuilder {

    @Override
    public FSM build() {
        var punctuations = new ArrayList<>(List.of(
                '.',
                ',',
                '(',
                ')'
        ));

        var fsm = new FSM();

        FSMState state0 = new FSMState("0", true, false);
        fsm.states.add(state0);
        FSMState stateEnd = new FSMState("end", false, true);
        fsm.states.add(stateEnd);

        for (Character punctuation : punctuations) {
            FSMTransition transition = new FSMTransition(state0, stateEnd, punctuation);
            fsm.transitions.add(transition);
        }

        return fsm;
    }

}
