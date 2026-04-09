package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.PushdownAutomaton;
import sql.to.mongodb.translator.scanner.Token;

@FunctionalInterface
public interface TokenProcessable {
    void execute(PushdownAutomaton pA, Token token);
}