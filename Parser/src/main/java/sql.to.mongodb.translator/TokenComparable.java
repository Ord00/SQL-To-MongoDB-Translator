package sql.to.mongodb.translator;

import sql.to.mongodb.translator.scanner.Token;

@FunctionalInterface
public interface TokenComparable {
    boolean execute(Token token);
}
