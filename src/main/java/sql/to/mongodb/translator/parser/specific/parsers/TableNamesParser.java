package sql.to.mongodb.translator.parser.specific.parsers;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class TableNamesParser extends Parser {

    public TableNamesParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseTableNames(List<Node> children, boolean isFirstTable, boolean isSubQuery) throws Exception {

        if (isFirstTable) {

            children.add(analyseTable());

            if (curTokenPos != tokens.size()
                    && !List.of("WHERE", "GROUP", "HAVING", "LIMIT", "SKIP", "ORDER").contains(curToken.lexeme)
                    && !(isSubQuery && curToken.lexeme.equals(")"))) {

                children.add(analyseJoin());
                children.add(analyseTable());
                children.add(analyseLogicalCondition(isSubQuery));

            }

        } else {

            children.add(analyseJoin());
            children.add(analyseTable());
            children.add(analyseLogicalCondition(isSubQuery));

        }

        if (curTokenPos == tokens.size()
                || List.of("WHERE", "GROUP", "HAVING", "LIMIT", "SKIP", "ORDER").contains(curToken.lexeme)
                || isSubQuery && curToken.lexeme.equals(")")) {

            return new Node(NodeType.TABLE_NAMES, children);

        } else {

            return analyseTableNames(children, false, isSubQuery);

        }
    }

    public static Node analyseTable() throws Exception {

        List<Node> children = new ArrayList<>();

        if (curToken.category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (curToken.lexeme.equals("(")) {

            children.add(tryAnalyse(true));

        } else {

            throw new Exception(String.format("Invalid table on %d!", curTokenPos));
        }

        analyseAlias(children);

        return new Node(NodeType.TABLE, children);
    }

    public static Node analyseJoin() throws Exception {

        List<Node> children = new ArrayList<>();

        switch (curToken.lexeme) {

            case "JOIN" -> {

                Node res = new Node(NodeType.TERMINAL, curToken);
                getNextToken();
                return res;

            }

            case "INNER" -> getNextToken();
            case "LEFT", "RIGHT" -> {

                getNextToken();
                if (curToken.lexeme.equals("OUTER")) {

                        getNextToken();

                }

            }

            default -> throw new Exception(String.format("Expected JOIN clause instead of %s on %d!",
                    curToken,
                    curTokenPos));

        }

        children.add(terminal(t -> t.lexeme.equals("JOIN"), "JOIN"));

        return new Node(NodeType.JOIN, children);
    }

    public static Node analyseLogicalCondition(boolean isSubQuery) throws Exception {

        List<Node> children = new ArrayList<>();

        switch (curToken.lexeme) {

            case "USING" -> {

                children.add(terminal(t -> t.lexeme.equals("("), "("));
                children.add(terminal(t -> t.category == Category.IDENTIFIER, "Identifier"));
                children.add(terminal(t -> t.lexeme.equals(")"), ")"));

            }

            case "ON" -> {

                stack.push(curToken);
                LogicalConditionParser.analyseLogicalCondition(children, isSubQuery);
                stack.pop();
            }

        }

        return new Node(NodeType.LOGICAL_CONDITION, children);
    }
}
