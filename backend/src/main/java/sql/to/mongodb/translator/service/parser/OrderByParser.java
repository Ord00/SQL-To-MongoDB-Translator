package sql.to.mongodb.translator.service.parser;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.List;

public class OrderByParser {

    public static Node analyseOrderBy(Parser parser,
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

            // Проверка на наличие скобок за пределами арифметического выражения
            if (parser.stack.peek().lexeme.equals("(")) {

                throw new SQLParseException(String.format("Invalid brackets in \"ORDER BY\" on %d!",
                        parser.curTokenPos));

            }

            if (token.category == Category.LITERAL) {

                throw new SQLParseException(String.format("Invalid member of \"ORDER BY\" on %d!",
                        parser.curTokenPos));

            } else if (token.category == Category.NUMBER && !token.lexeme.equals("NON")) {

                int curNum = Integer.parseInt(token.lexeme);

                if (curNum <= 0 || Integer.parseInt(parser.stack.peek().lexeme) < curNum) {

                    throw new SQLParseException(String.format("Invalid constant number in \"ORDER BY\" on %d!",
                            parser.curTokenPos));

                }
            }

        } else {

            throw new SQLParseException(String.format("Invalid member of \"ORDER BY\" on %d!",
                    parser.curTokenPos));

        }

        if (parser.curToken.lexeme.equals("DESC")
                || parser.curToken.lexeme.equals("ASC")) {

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

        } else {

            children.add(new Node(NodeType.TERMINAL, new Token("ASC", Category.KEYWORD)));

        }

        if (parser.curToken.lexeme.equals(",")) {

            parser.getNextToken();
            return analyseOrderBy(parser, children, isSubQuery);

        } else if (parser.curTokenPos == parser.tokens.size()
                || parser.curToken.category == Category.KEYWORD
                || isSubQuery && parser.curToken.lexeme.equals(")")) {

            return new Node(NodeType.ORDER_BY, children);

        } else {

            throw new SQLParseException(String.format("Invalid link between members of \"ORDER BY\" on %d!",
                    parser.curTokenPos));

        }
    }
}
