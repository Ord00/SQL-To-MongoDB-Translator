package sql.to.mongodb.translator.parser.specific.parsers;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class FunctionsParser extends Parser {

    public FunctionsParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static void analyseAggregate(List<Node> children, boolean isColumn) throws Exception {

        if (!isColumn && !stack.peek().lexeme.equals("HAVING")) {

            throw new Exception(String.format("Aggregate function in incorrect section on %d!", curTokenPos));

        }

        stack.push(curToken);

        List<Node> aggregateChildren = new ArrayList<>();
        aggregateChildren.add(new Node(NodeType.TERMINAL, curToken));

        getNextToken();

        checkToken(t -> t.lexeme.equals("("), "(");

        if (stack.pop().lexeme.equals("COUNT") && curToken.category == Category.ALL) {

            aggregateChildren.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (!analyseOperand(aggregateChildren,
                null,
                t -> false,
                isColumn)) {

            throw new Exception(String.format("Incorrect attribute of aggregate function on %d!", curTokenPos));

        }

        checkToken(t -> t.lexeme.equals(")"), ")");

        children.add(new Node(NodeType.AGGREGATE, aggregateChildren));
    }

}
