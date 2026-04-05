package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.parser.Node;

import java.util.Iterator;

@FunctionalInterface
public interface ExpressionConvertible {
    String execute(Iterator<Node> iterator);
}
