package sql.to.mongodb.translator.entities.builders;

import sql.to.mongodb.translator.entities.finite.automata.FSM;
import sql.to.mongodb.translator.entities.finite.automata.FSMState;
import sql.to.mongodb.translator.entities.finite.automata.FSMTransition;

import java.util.ArrayList;
import java.util.List;

public class OperatorFSMBuilder extends FSMBuilder {

    @Override
    public FSM build() {
        List<String> operators = new ArrayList<>(List.of(
                "=",
                "<",
                ">",
                "is",
                "like"
        ));

        var fsm = new FSM();

        FSMState state0 = new FSMState("0", true, false);
        fsm.states.add(state0);
        FSMState stateEnd = new FSMState("end", false, true);
        fsm.states.add(stateEnd);

        for (String op : operators) {

            var currentState = state0;
            char[] subOp = op.substring(0, op.length()-1).toCharArray();

            for (var c : subOp) {
                var state = new FSMState(String.format("%s%s", op, c), false, false);
                fsm.states.add(state);
                FSMTransition transition = new FSMTransition(currentState, state, c);
                fsm.transitions.add(transition);

                currentState = state;
            }

            FSMTransition transitionEnd = new FSMTransition(currentState, stateEnd, op.charAt(op.length() - 1));
            fsm.transitions.add(transitionEnd);
        }

        return fsm;
    }
}
