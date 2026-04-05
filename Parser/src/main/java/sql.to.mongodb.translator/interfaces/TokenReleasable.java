package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.PushdownAutomaton;

@FunctionalInterface
public interface TokenReleasable {
    void execute(PushdownAutomaton pA);
}
