package sql.to.mongodb.translator.parser.specific.parsers;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class FunctionsParser {

    public static void analyseAggregate(Parser parser, List<Node> children, boolean isColumn) throws Exception {

        if (!isColumn && !parser.stack.peek().lexeme.equals("HAVING")) {

            throw new Exception(String.format("Aggregate function in incorrect section on %d!", parser.curTokenPos));

        }

        parser.stack.push(parser.curToken);

        List<Node> aggregateChildren = new ArrayList<>();
        aggregateChildren.add(new Node(NodeType.TERMINAL, parser.curToken));

        parser.getNextToken();

        parser.checkToken(t -> t.lexeme.equals("("), "(");

        if (parser.stack.pop().lexeme.equals("COUNT") && parser.curToken.category == Category.ALL) {

            aggregateChildren.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

        } else if (!parser.analyseOperand(aggregateChildren,
                null,
                t -> false,
                isColumn)) {

            throw new Exception(String.format("Incorrect attribute of aggregate function on %d!", parser.curTokenPos));

        }

        parser.checkToken(t -> t.lexeme.equals(")"), ")");

        children.add(new Node(NodeType.AGGREGATE, aggregateChildren));
    }

}
