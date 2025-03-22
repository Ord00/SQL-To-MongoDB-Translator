package sql.to.mongodb.translator.service.parser;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

public class FunctionsParser {

    public static void analyseAggregate(Parser parser,
                                        List<Node> children,
                                        boolean isColumn) throws SQLParseException {

        if (!isColumn && !parser.stack.peek().lexeme.equals("HAVING")) {

            throw new SQLParseException(String.format("Aggregate function in incorrect section on %d!",
                      parser.curTokenPos));

        }

        parser.stack.push(parser.curToken);

        List<Node> aggregateChildren = new ArrayList<>();
        aggregateChildren.add(new Node(NodeType.TERMINAL, parser.curToken));

        parser.getNextToken();

        parser.checkToken(t -> t.lexeme.equals("("), "(");

        Token aggregateFunction = parser.stack.pop();

        if (aggregateFunction.lexeme.equals("COUNT")
                && parser.curToken.category == Category.ALL) {

            aggregateChildren.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

        } else {

            if (parser.curToken.lexeme.equals("DISTINCT")) {

                aggregateChildren.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();

            }

            parser.stack.push(aggregateFunction);

            if (parser.analyseOperand(aggregateChildren,
                    t -> parser.stack.push(t),
                    _ -> false,
                    isColumn)) {

                if (parser.stack.pop().category == Category.LITERAL
                        && !parser.stack.pop().lexeme.equals("COUNT")) {

                    throw new SQLParseException(String.format("Incorrect attribute of aggregate function on %d!",
                            parser.curTokenPos));

                }

            } else {

                throw new SQLParseException(String.format("Incorrect attribute of aggregate function on %d!",
                        parser.curTokenPos));

            }
        }

        parser.checkToken(t -> t.lexeme.equals(")"), ")");

        children.add(new Node(NodeType.AGGREGATE, aggregateChildren));
    }

}
