package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class ColumnNamesParser extends Parser {

    public ColumnNamesParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseColumnNames(List<Node> children) throws Exception {

        if (curToken.category == Category.IDENTIFIER
        || curToken.category.equals(Category.NUMBER)
        || curToken.category.equals(Category.LITERAL)) {
            children.add(terminal(t -> true, NodeType.TERMINAL));
        } else if (curToken.category == Category.ALL) {
            children.add(terminal(t -> true, NodeType.TERMINAL));
            if (!curToken.lexeme.equals("FROM")) {
                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
            }
            return new Node(NodeType.COLUMN_NAMES, children);
        } else {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        if (curToken.category.equals(Category.PUNCTUATION) && curToken.lexeme.equals(",")) {
            return analyseColumnNames(children);
        } else if (curToken.lexeme.equals("FROM")) {
            return new Node(NodeType.COLUMN_NAMES, children);
        } else {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }
    }
}
