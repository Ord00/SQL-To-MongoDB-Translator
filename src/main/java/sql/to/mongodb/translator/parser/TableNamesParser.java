package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.exceptions.SQLParseException;

import java.util.ArrayList;
import java.util.List;

public class TableNamesParser {

    public static Node analyseTableNames(Parser parser,
                                         List<Node> children,
                                         boolean isFirstTable,
                                         boolean isSubQuery) throws SQLParseException {

        if (isFirstTable) {

            children.add(analyseTable(parser));

            if (parser.curTokenPos != parser.tokens.size()
                    && !List.of("WHERE", "GROUP", "HAVING", "LIMIT", "SKIP", "ORDER").contains(parser.curToken.lexeme)
                    && !(isSubQuery && parser.curToken.lexeme.equals(")"))) {

                children.add(analyseJoin(parser));
                children.add(analyseTable(parser));
                children.add(analyseLogicalCondition(parser, isSubQuery));

            }

        } else {

            children.add(analyseJoin(parser));
            children.add(analyseTable(parser));
            children.add(analyseLogicalCondition(parser, isSubQuery));

        }

        if (parser.curTokenPos == parser.tokens.size()
                || List.of("WHERE", "GROUP", "HAVING", "LIMIT", "SKIP", "ORDER").contains(parser.curToken.lexeme)
                || isSubQuery && parser.curToken.lexeme.equals(")")) {

            return new Node(NodeType.TABLE_NAMES, children);

        } else {

            return analyseTableNames(parser, children, false, isSubQuery);

        }
    }

    public static Node analyseTable(Parser parser) throws SQLParseException {

        List<Node> children = new ArrayList<>();

        if (parser.curToken.category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

        } else if (parser.curToken.lexeme.equals("(")) {

            parser.analyseSubquery(children);

            parser.stack.pop();

            parser.checkToken(t -> t.lexeme.equals(")"), ")");

        } else {

            throw new SQLParseException(String.format("Invalid table on %d!",
                    parser.curTokenPos));
        }

        parser.analyseAlias(children);

        return new Node(NodeType.TABLE, children);
    }

    public static Node analyseJoin(Parser parser) throws SQLParseException {

        List<Node> children = new ArrayList<>();

        switch (parser.curToken.lexeme) {

            case "JOIN" -> {

                Node res = new Node(NodeType.TERMINAL, parser.curToken);
                parser.getNextToken();
                return res;

            }

            case "INNER" -> parser.getNextToken();
            case "LEFT", "RIGHT" -> {

                parser.getNextToken();
                if (parser.curToken.lexeme.equals("OUTER")) {

                    parser.getNextToken();

                }

            }

            default -> throw new SQLParseException(String.format("Expected JOIN clause instead of %s on %d!",
                    parser.curToken,
                    parser.curTokenPos));

        }

        children.add(parser.terminal(t -> t.lexeme.equals("JOIN"), "JOIN"));

        return new Node(NodeType.JOIN, children);
    }

    public static Node analyseLogicalCondition(Parser parser,
                                               boolean isSubQuery) throws SQLParseException {

        List<Node> children = new ArrayList<>();

        switch (parser.curToken.lexeme) {

            case "USING" -> {

                children.add(parser.terminal(t -> t.lexeme.equals("("), "("));
                children.add(parser.terminal(t -> t.category == Category.IDENTIFIER, "Identifier"));
                children.add(parser.terminal(t -> t.lexeme.equals(")"), ")"));

            }

            case "ON" -> {

                parser.stack.push(parser.curToken);
                LogicalConditionParser.analyseLogicalCondition(parser, children, isSubQuery);
                parser.stack.pop();
            }

        }

        return new Node(NodeType.LOGICAL_CONDITION, children);
    }
}
