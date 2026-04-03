package sql.to.mongodb.translator.special.cases;

import sql.to.mongodb.translator.PushdownAutomaton;
import sql.to.mongodb.translator.SQLParseException;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public class BracketsParser {

    public static void analysePreProcessBrackets(PushdownAutomaton pA,
                                                 List<Node> children) {

        while (pA.curToken().lexeme.equals("(")) {

            pA.push(pA.curToken());
            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        }

        if (pA.curToken().lexeme.equals("SELECT")) {

            pA.pop();
            children.removeLast();
            pA.getPrevToken();

        }
    }

    public static void analysePostProcessBrackets(PushdownAutomaton pA,
                                                  List<Node> children,
                                                  boolean isSubQuery) throws SQLParseException {

        Token bracketToken = pA.peek();

        while (pA.curToken().lexeme.equals(")") && bracketToken.lexeme.equals("(")) {

            pA.pop();
            bracketToken = pA.peek();

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        }

        if (!isSubQuery && pA.curToken().lexeme.equals(")")) {

            throw new SQLParseException(String.format("Invalid brackets on %d!",
                    pA.curTokenPos()));

        }

    }

}
