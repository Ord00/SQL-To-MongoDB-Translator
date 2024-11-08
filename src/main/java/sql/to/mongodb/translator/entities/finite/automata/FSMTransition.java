package sql.to.mongodb.translator.entities.finite.automata;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        FSMTransition other = (FSMTransition) obj;
        return start.equals(other.start) && item.equals(other.item);
    }
}
