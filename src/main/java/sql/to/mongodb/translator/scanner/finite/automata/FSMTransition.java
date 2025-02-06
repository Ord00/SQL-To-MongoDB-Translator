package sql.to.mongodb.translator.scanner.finite.automata;

public class FSMTransition {

    public FSMState start;

    public FSMState end;

    public Character item;

    public FSMTransition(FSMState start, FSMState end, Character item) {
        this.start = start;
        this.end = end;
        this.item = item;
    }

    public FSMState transit(char c) {

        if (item == null || item == c) {
            return end;
        }
        return null;
    }
}
