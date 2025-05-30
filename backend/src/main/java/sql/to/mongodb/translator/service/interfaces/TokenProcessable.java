package sql.to.mongodb.translator.service.interfaces;

import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;

@FunctionalInterface
public interface TokenProcessable {
    void execute(PushdownAutomaton pA, Token token);
}