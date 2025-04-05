package sql.to.mongodb.translator.service.interfaces;

import sql.to.mongodb.translator.service.parser.PushdownAutomaton;

@FunctionalInterface
public interface TokenReleasable {
    void execute(PushdownAutomaton pA);
}
