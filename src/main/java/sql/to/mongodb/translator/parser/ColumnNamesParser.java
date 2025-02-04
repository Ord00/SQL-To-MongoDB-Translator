package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.List;

public class ColumnNamesParser extends Parser {

    public ColumnNamesParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseColumnNames(List<Node> children) throws Exception {

        if (curToken.category == Category.ALL) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (!curToken.lexeme.equals("FROM")) {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            }
            return new Node(NodeType.COLUMN_NAMES, children);

        } else if (curToken.category == Category.AGGREGATE) {

            FunctionsParser.analyseAggregate(children, true);

        } else if (!analyseOperand(children)) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        return switch (curToken.lexeme) {

            case "," -> {

                getNextToken();
                yield analyseColumnNames(children);

            }
            case "FROM" -> new Node(NodeType.COLUMN_NAMES, children);
            default -> throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        };
    }
}
