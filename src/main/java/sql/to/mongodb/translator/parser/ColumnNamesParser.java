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

    public static Node analyseSelect() throws Exception {

        List<Node> children = new ArrayList<>();

        if (curToken.category == Category.IDENTIFIER) {
            children.add(terminal(t -> true));
            analyseIdentifier(children);
            return new Node(NodeType.COLUMN_NAMES, children);
        }

        if (curToken.category == Category.ALL) {
            children.add(terminal(t -> true));
            analyseAll();
            return new Node(NodeType.COLUMN_NAMES, children);
        }

        throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
    }

    private static void analyseIdentifier(List<Node> children) throws Exception {

        if (curToken.category.equals(Category.KEYWORD)
                && curToken.lexeme.equals("FROM") // + проверка на терминальность последнего
        ) {
            return;
        }

        children.add(terminal(t -> t.category.equals(Category.PUNCTUATION) && t.lexeme.equals(",")));
        analyseComma(children);
    }

    private static void analyseAll() throws Exception {
        if (!curToken.category.equals(Category.KEYWORD)
                || !curToken.lexeme.equals("FROM") // + проверка на терминальность последнего
        ) {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }
    }

    private static void analyseComma(List<Node> children) throws Exception {

        if (curToken.category == Category.IDENTIFIER) {
            children.add(terminal(t -> true));
            analyseIdentifier(children);
            return;
        }

        throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
    }
}
