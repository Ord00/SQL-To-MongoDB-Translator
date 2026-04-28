package sql.to.mongodb.translator.special.cases;

import sql.to.mongodb.translator.PushdownAutomaton;
import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.interfaces.TokenComparable;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;

public class TokenHandler {

    public static void checkToken(PushdownAutomaton pA,
                                  TokenComparable comparator,
                                  String expectedToken) throws SQLParseException {

        if (comparator.execute(pA.curToken())) {

            pA.getNextToken();

        } else {

            throw new SQLParseException(String.format("%s expected instead of %s on %d!",
                    expectedToken,
                    pA.curToken().lexeme,
                    pA.curTokenPos()));

        }

    }

    public static Node terminal(PushdownAutomaton pA,
                                TokenComparable comparator,
                                String expectedToken) throws SQLParseException {

        if (comparator.execute(pA.curToken())) {

            Node terminalNode = new Node(NodeType.TERMINAL, pA.curToken());
            pA.getNextToken();
            return terminalNode;

        } else {

            throw new SQLParseException(String.format("%s expected instead of %s on %d!",
                    expectedToken,
                    pA.curToken().lexeme,
                    pA.curTokenPos()));

        }
    }

}
