package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.entities.Token;

@FunctionalInterface
public interface LambdaCallable {
    void execute(Token token);
}