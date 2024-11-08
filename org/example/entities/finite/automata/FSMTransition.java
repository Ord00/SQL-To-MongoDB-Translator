package org.example.entities.finite.automata;

public class FSMTransition {

    public FSMState start;

    public FSMState end;

    public Character item;

    public FSMTransition(FSMState start, FSMState end, Character item) {
        this.start = start;
        this.end = end;
        this.item = item;
    }

    public FSMState Transition(char c) {
        if (item == c) {
            return end;
        }
        return null;
    }
}
