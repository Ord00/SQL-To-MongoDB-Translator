package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class FunctionsParser extends Parser {

    public  FunctionsParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static void analyseAggregate(List<Node> children, boolean isColumn) throws Exception {

        if (!isColumn && !stack.peek().lexeme.equals("HAVING")) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        stack.push(curToken);
        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();
        children.add(terminal(t -> t.lexeme.equals("(")));

        if (stack.pop().lexeme.equals("COUNT") && curToken.category == Category.ALL) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (!analyseOperand(children, null, t -> false)) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        children.add(terminal(t -> t.lexeme.equals(")")));
    }

}
