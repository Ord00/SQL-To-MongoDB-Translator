package sql.to.mongodb.translator.special.cases;

import sql.to.mongodb.translator.PushdownAutomaton;
import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Category;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

import static sql.to.mongodb.translator.special.cases.TokenHandler.terminal;

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

            children.add(new Node(NodeType.TERMINAL,
                    new Token("AS" , Category.KEYWORD)));

            children.add(terminal(pA,
                    t -> t.category.equals(Category.IDENTIFIER),
                    "Identifier"));

        } else if (children.getLast().getNodeType() == NodeType.QUERY) {

            throw new SQLParseException(String.format("Subquery is missing elias on %d!",
                    pA.curTokenPos()));

        }

    }

}
