package sql.to.mongodb.translator.service.interfaces;

import sql.to.mongodb.translator.service.parser.Node;

import java.util.Iterator;

@FunctionalInterface
public interface ExpressionConvertible {
    String execute(Iterator<Node> iterator);
}
