package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.scanner.Token;

@FunctionalInterface
public interface LambdaReleasable {
    void execute();
}
