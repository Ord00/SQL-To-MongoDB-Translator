package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.special.cases.OperandParser.analyseOperand;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.checkToken;

public class FunctionsParser {

    public static void analyseAggregate(PushdownAutomaton pA,
                                        List<Node> children,
                                        boolean isColumn) throws SQLParseException {

        if (!isColumn && !pA.peek().lexeme.equals("HAVING")) {

            throw new SQLParseException(String.format("Aggregate function in incorrect section on %d!",
                    pA.curTokenPos()));

        }

        pA.push(pA.curToken());

        List<Node> aggregateChildren = new ArrayList<>();
        aggregateChildren.add(new Node(NodeType.TERMINAL, pA.curToken()));

        pA.getNextToken();

        checkToken(pA, t -> t.lexeme.equals("("), "(");

        Token aggregateFunction = pA.pop();

        if (aggregateFunction.lexeme.equals("COUNT")
                && pA.curToken().category == Category.ALL) {

            aggregateChildren.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        } else {

            if (pA.curToken().lexeme.equals("DISTINCT")) {

                aggregateChildren.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

            }

            pA.push(aggregateFunction);

            if (analyseOperand(pA,
                    aggregateChildren,
                    PushdownAutomaton::push,
                    _ -> false,
                    isColumn)) {

                if (pA.pop().category == Category.LITERAL
                        && !pA.pop().lexeme.equals("COUNT")) {

                    throw new SQLParseException(String.format("Incorrect attribute of aggregate function on %d!",
                            pA.curTokenPos()));

                }

            } else {

                throw new SQLParseException(String.format("Incorrect attribute of aggregate function on %d!",
                        pA.curTokenPos()));

            }
        }

        checkToken(pA,
                t -> t.lexeme.equals(")"), ")");

        children.add(new Node(NodeType.AGGREGATE, aggregateChildren));

    }

}
