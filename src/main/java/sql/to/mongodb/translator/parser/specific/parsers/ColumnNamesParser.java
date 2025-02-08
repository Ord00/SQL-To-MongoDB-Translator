package sql.to.mongodb.translator.parser.specific.parsers;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class ColumnNamesParser extends Parser {

    public ColumnNamesParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseColumnNames(List<Node> children) throws Exception {

        if (curToken.category == Category.ALL) {

            stack.push(new Token("2", Category.PROC_NUMBER));

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (!curToken.lexeme.equals("FROM")) {

                throw new Exception(String.format("Expected \"FROM\" instead of %s on %d!",
                        curToken,
                        curTokenPos));

            }

            return new Node(NodeType.COLUMN_NAMES, children);

        } else if (curToken.category == Category.AGGREGATE) {

            processColumnThroughStack(curToken);

            FunctionsParser.analyseAggregate(children, true);

            analyseArithmeticExpression(children,
                    true,
                    Parser::processColumnThroughStack,
                    Parser::releaseColumnThroughStack);

        } else {

            if (!analyseOperand(children,
                    Parser::processColumnThroughStack,
                    t -> t.category != Category.PROC_NUMBER,
                    true)) {

                throw new Exception(String.format("Incorrect attribute on %d!", curTokenPos));

            } else {

                analyseArithmeticExpression(children,
                        true,
                        Parser::processColumnThroughStack,
                        Parser::releaseColumnThroughStack);

            }
        }

        analyseAlias(children);

        return switch (curToken.lexeme) {

            case "," -> {

                getNextToken();
                yield analyseColumnNames(children);

            }
            case "FROM" -> new Node(NodeType.COLUMN_NAMES, children);
            default -> throw new Exception(String.format("Invalid link between attributes on %d!", curTokenPos));

        };
    }
}
