package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class TableNamesParser extends Parser {

    public TableNamesParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseTableNames(List<Node> children, boolean isFirstTable) throws Exception {

        if (isFirstTable) {

            children.add(analyseTable(true));

            if (!List.of("WHERE", "GROUP", "HAVING", "LIMIT", "SKIP", "ORDER").contains(curToken.lexeme)) {

                children.add(analyseJoin());
                children.add(analyseTable(false));
                children.add(analyseLogicalCondition());

            }

        } else {

            children.add(analyseJoin());
            children.add(analyseTable(false));
            children.add(analyseLogicalCondition());

        }

        if (curTokenPos == tokens.size()
                || List.of("WHERE", "GROUP", "HAVING", "LIMIT", "SKIP", "ORDER").contains(curToken.lexeme)) {

            return new Node(NodeType.TABLE_NAMES, children);

        } else {

            return analyseTableNames(children, false);

        }
    }

    public static Node analyseTable(boolean isFirstTable) throws Exception {

        List<Node> children = new ArrayList<>();

        if (curToken.category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (curToken.lexeme.equals("(")) {

            children.add(tryAnalyse(true));

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        if (curToken.lexeme.equals("AS")) {

            getNextToken();
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER)));

        } else if (curToken.category.equals(Category.IDENTIFIER)) {

            getNextToken();

        } else if (children.getLast().getNodeType() == NodeType.QUERY) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        return new Node(NodeType.TABLE, children);
    }

    public static Node analyseJoin() throws Exception {

        List<Node> children = new ArrayList<>();

        switch (curToken.lexeme) {

            case "INNER" -> getNextToken();
            case "LEFT", "RIGHT" -> {

                getNextToken();
                if (curToken.lexeme.equals("OUTER")) {
                        getNextToken();
                }

            }

            default -> throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        children.add(terminal(t -> t.lexeme.equals("JOIN")));

        return new Node(NodeType.JOIN, children);
    }

    public static Node analyseLogicalCondition() throws Exception {

        List<Node> children = new ArrayList<>();

        switch (curToken.lexeme) {

            case "USING" -> {

                children.add(terminal(t -> t.lexeme.equals("(")));
                children.add(terminal(t -> t.category == Category.IDENTIFIER));
                children.add(terminal(t -> t.lexeme.equals(")")));

            }

            case "ON" -> LogicalConditionParser.analyseLogicalCondition(children);

        }

        return new Node(NodeType.LOGICAL_CONDITION, children);
    }
}
