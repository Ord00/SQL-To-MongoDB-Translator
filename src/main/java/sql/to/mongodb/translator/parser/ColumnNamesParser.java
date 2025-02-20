package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class ColumnNamesParser {

    public static Node analyseColumnNames(Parser parser, List<Node> children) throws SQLParseException {

        parser.preProcessBrackets(children);

        final Token[] identifierToken = new Token[1];

        if (parser.curToken.category == Category.ALL) {

            parser.stack.push(new Token("2", Category.PROC_NUMBER));

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

            if (!parser.curToken.lexeme.equals("FROM")) {

                throw new SQLParseException(String.format("Expected \"FROM\" instead of %s on %d!",
                        parser.curToken,
                        parser.curTokenPos));

            }

            return new Node(NodeType.COLUMN_NAMES, children);

        } else if (parser.curToken.category == Category.AGGREGATE) {

            parser.processColumnThroughStack(parser.curToken);

            FunctionsParser.analyseAggregate(parser, children, true);

            parser.analyseArithmeticExpression(children,
                    true,
                    parser::processColumnThroughStack,
                    parser::releaseColumnThroughStack);

        } else if (parser.analyseOperand(children,
                t -> identifierToken[0] = t,
                t -> t.category != Category.PROC_NUMBER,
                true)) {

            if (!parser.analyseArithmeticExpression(children,
                    true,
                    parser::processColumnThroughStack,
                    null)) {

                parser.processColumnThroughStack(identifierToken[0]);

            }

        } else {

            throw new SQLParseException(String.format("Incorrect attribute on %d!",
                    parser.curTokenPos));

        }

        // Проверка на наличие скобок за пределами арифметического выражения
        if (parser.stack.peek().lexeme.equals("(")) {

            throw new SQLParseException(String.format("Invalid brackets in \"FROM\" on %d!",
                    parser.curTokenPos));

        }

        parser.analyseAlias(children);

        return switch (parser.curToken.lexeme) {

            case "," -> {

                parser.getNextToken();
                yield analyseColumnNames(parser, children);

            }
            case "FROM" -> new Node(NodeType.COLUMN_NAMES, children);
            default -> throw new SQLParseException(String.format("Invalid link between attributes on %d!",
                    parser.curTokenPos));

        };
    }
}
