package sql.to.mongodb.translator;

@FunctionalInterface
public interface TokenReleasable {
    void execute(PushdownAutomaton pA);
}
