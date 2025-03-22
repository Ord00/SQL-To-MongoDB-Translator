package sql.to.mongodb.translator.service.interfaces;

import sql.to.mongodb.translator.service.scanner.Token;

@FunctionalInterface
public interface TokenComparable {
    boolean execute(Token token);
}
