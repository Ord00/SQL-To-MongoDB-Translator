package sql.to.mongodb.translator.parser.specific.parsers;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class ColumnNamesParser {

    public static Node analyseColumnNames(Parser parser, List<Node> children) throws Exception {

        if (parser.curToken.category == Category.ALL) {

            parser.stack.push(new Token("2", Category.PROC_NUMBER));

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

            if (!parser.curToken.lexeme.equals("FROM")) {

                throw new Exception(String.format("Expected \"FROM\" instead of %s on %d!",
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

        } else {

            if (!parser.analyseOperand(children,
                    parser::processColumnThroughStack,
                    t -> t.category != Category.PROC_NUMBER,
                    true)) {

                throw new Exception(String.format("Incorrect attribute on %d!", parser.curTokenPos));

            } else {

                parser.analyseArithmeticExpression(children,
                        true,
                        parser::processColumnThroughStack,
                        parser::releaseColumnThroughStack);

            }
        }

        parser.analyseAlias(children);

        return switch (parser.curToken.lexeme) {

            case "," -> {

                parser.getNextToken();
                yield analyseColumnNames(parser, children);

            }
            case "FROM" -> new Node(NodeType.COLUMN_NAMES, children);
            default -> throw new Exception(String.format("Invalid link between attributes on %d!", parser.curTokenPos));

        };
    }
}
