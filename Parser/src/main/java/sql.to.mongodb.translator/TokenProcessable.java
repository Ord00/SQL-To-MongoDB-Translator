package sql.to.mongodb.translator;

import sql.to.mongodb.translator.scanner.Token;

@FunctionalInterface
public interface TokenProcessable {
    void execute(PushdownAutomaton pA, Token token);
}