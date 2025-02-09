package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class GroupByParser {

    public static Node analyseGroupBy(Parser parser, List<Node> children, boolean isSubQuery) throws Exception {

        if (parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            parser.analyseArithmeticExpression(children,
                    false,
                    t -> parser.stack.push(t),
                    () -> parser.stack.pop());

            Token token = parser.stack.pop();

            if (token.category == Category.LITERAL
                    || token.category == Category.NUMBER && !token.lexeme.equals("NON")) {

                throw new Exception(String.format("Invalid member of GROUP BY on %d!", parser.curTokenPos));

            }

        } else {

            throw new Exception(String.format("Invalid member of GROUP BY on %d!", parser.curTokenPos));

        }

        if (parser.curToken.lexeme.equals(",")) {

            parser.getNextToken();
            return analyseGroupBy(parser, children, isSubQuery);

        } else if (parser.curTokenPos == parser.tokens.size() || parser.curToken.category == Category.KEYWORD
                || isSubQuery && parser.curToken.lexeme.equals(")")) {

            return new Node(NodeType.GROUP_BY, children);

        } else {

            throw new Exception(String.format("Invalid link between members of GROUP BY on %d!", parser.curTokenPos));

        }
    }
}
