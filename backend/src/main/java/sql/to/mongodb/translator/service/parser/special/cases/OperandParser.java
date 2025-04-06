package sql.to.mongodb.translator.service.parser.special.cases;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.interfaces.TokenComparable;
import sql.to.mongodb.translator.service.interfaces.TokenProcessable;
import sql.to.mongodb.translator.service.parser.main.cases.CaseParser;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.parser.dml.SelectParser;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.checkToken;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.terminal;

public class OperandParser {

    public static boolean analyseOperand(PushdownAutomaton pA,
                                         List<Node> children,
                                         TokenProcessable processToken,
                                         TokenComparable subQueryCheck,
                                         boolean isColumn) throws SQLParseException {

        boolean isFound = true;

        if (pA.curToken().category == Category.IDENTIFIER) {

            if (processToken != null) {

                processToken.execute(pA, pA.curToken());

            }

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

            if (pA.curToken().lexeme.equals(".")) {

                List<Node> identifierChildren = new ArrayList<>();
                identifierChildren.add(children.removeLast());

                pA.getNextToken();

                identifierChildren.add(terminal(pA,
                        t -> t.category == Category.IDENTIFIER
                                || isColumn && t.category == Category.ALL,
                        "Identifier or \"*\" in case of column"));

                children.add(new Node(NodeType.IDENTIFIER, identifierChildren));
            }

        } else if (pA.curToken().category.equals(Category.NUMBER)
                || pA.curToken().category.equals(Category.LITERAL)) {

            if (processToken != null) {

                processToken.execute(pA, pA.curToken());

            }

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        } else if (pA.curToken().lexeme.equals("(")) {

            SelectParser.analyseSubquery(pA, children);

            checkToken(pA,
                    t -> t.lexeme.equals(")"), ")");

            if (!subQueryCheck.execute(pA.peek())) {

                throw new SQLParseException(String.format("Invalid subquery type on %d!",
                        pA.curTokenPos()));

            }

        } else if (pA.curToken().lexeme.equals("CASE")) {

            if (processToken != null) {

                processToken.execute(pA, CaseParser.analyseCase(pA,
                        children,
                        null,
                        true));

            }

        } else {

            isFound = false;

        }

        return isFound;

    }

}
