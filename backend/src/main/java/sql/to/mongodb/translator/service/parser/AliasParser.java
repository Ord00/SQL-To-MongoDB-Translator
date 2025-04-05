package sql.to.mongodb.translator.service.parser;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.List;

import static sql.to.mongodb.translator.service.parser.TokenHandler.terminal;

public class AliasParser {

    public static void analyseAlias(PushdownAutomaton pA,
                      List<Node> children) throws SQLParseException {

        if (pA.curToken().lexeme.equals("AS")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();
            children.add(terminal(pA,
                    t -> t.category.equals(Category.IDENTIFIER),
                    "Identifier"));

        } else if (pA.curToken().category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, new Token("AS" , Category.KEYWORD)));
            children.add(terminal(pA,
                    t -> t.category.equals(Category.IDENTIFIER),
                    "Identifier"));

        } else if (children.getLast().getNodeType() == NodeType.QUERY) {

            throw new SQLParseException(String.format("Subquery is missing elias on %d!",
                    pA.curTokenPos()));

        }

    }

}
