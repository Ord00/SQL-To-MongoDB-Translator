package sql.to.mongodb.translator.service.parser;

import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.scanner.Token;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;

import java.util.List;

public class GroupByParser {

    public static Node analyseGroupBy(Parser parser,
                                      List<Node> children,
                                      boolean isSubQuery) throws SQLParseException {

        parser.preProcessBrackets(children);

        if (parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            ArithmeticParser.analyseArithmeticExpression(parser,
                    children,
                    false,
                    t -> parser.stack.push(t),
                    () -> parser.stack.pop());

            Token token = parser.stack.pop();

            if (token.category == Category.LITERAL
                    || token.category == Category.NUMBER && !token.lexeme.equals("NON")) {

                throw new SQLParseException(String.format("Invalid member of \"GROUP BY\" on %d!",
                        parser.curTokenPos));

            }

        } else {

            throw new SQLParseException(String.format("Invalid member of \"GROUP BY\" on %d!",
                    parser.curTokenPos));

        }

        // Проверка на наличие скобок за пределами арифметического выражения
        if (parser.stack.peek().lexeme.equals("(")) {

            throw new SQLParseException(String.format("Invalid brackets in \"GROUP BY\" on %d!",
                    parser.curTokenPos));

        }

        if (parser.curToken.lexeme.equals(",")) {

            parser.getNextToken();
            return analyseGroupBy(parser, children, isSubQuery);

        } else if (parser.curTokenPos == parser.tokens.size()
                || parser.curToken.category == Category.KEYWORD
                || isSubQuery && parser.curToken.lexeme.equals(")")) {

            return new Node(NodeType.GROUP_BY, children);

        } else {

            throw new SQLParseException(String.format("Invalid link between members of \"GROUP BY\" on %d!",
                    parser.curTokenPos));

        }
    }
}
