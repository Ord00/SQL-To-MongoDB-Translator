package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class GroupByParser extends Parser {

    public GroupByParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseGroupBy(List<Node> children) throws Exception {

        if (analyseOperand(children,
                t -> stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            Token token = stack.pop();

            if (token.category == Category.LITERAL || token.category == Category.NUMBER) {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            }

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        if (curToken.lexeme.equals(",")) {

            getNextToken();
            return analyseGroupBy(children);

        } else if (curTokenPos == tokens.size() || curToken.category == Category.KEYWORD) {

            return new Node(NodeType.GROUP_BY, children);

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }
    }
}
