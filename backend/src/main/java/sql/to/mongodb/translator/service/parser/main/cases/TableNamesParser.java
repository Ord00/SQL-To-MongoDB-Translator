package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.parser.dml.SelectParser;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.special.cases.AliasParser.analyseAlias;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.checkToken;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.terminal;

public class TableNamesParser {

    public static Node analyseTableNames(PushdownAutomaton pA,
                                         List<Node> children,
                                         boolean isFirstTable,
                                         boolean isSubQuery) throws SQLParseException {

        if (isFirstTable) {

            children.add(analyseTable(pA));

            if (!pA.isEnd()
                    && !List.of("WHERE", "GROUP", "HAVING", "LIMIT", "OFFSET", "ORDER")
                    .contains(pA.curToken().lexeme)
                    && !(isSubQuery && pA.curToken().lexeme.equals(")"))) {

                children.add(analyseJoin(pA));
                children.add(analyseTable(pA));
                children.add(analyseLogicalCondition(pA, isSubQuery));

            }

        } else {

            children.add(analyseJoin(pA));
            children.add(analyseTable(pA));
            children.add(analyseLogicalCondition(pA, isSubQuery));

        }

        if (pA.isEnd()
                || List.of("WHERE", "GROUP", "HAVING", "LIMIT", "OFFSET", "ORDER")
                .contains(pA.curToken().lexeme)
                || isSubQuery && pA.curToken().lexeme.equals(")")) {

            return new Node(NodeType.TABLE_NAMES, children);

        } else {

            return analyseTableNames(pA,
                    children,
                    false,
                    isSubQuery);

        }
    }

    public static Node analyseTable(PushdownAutomaton pA) throws SQLParseException {

        List<Node> children = new ArrayList<>();

        if (pA.curToken().category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        } else if (pA.curToken().lexeme.equals("(")) {

            SelectParser.analyseSubquery(pA, children);

            pA.pop();

            checkToken(pA,
                    t -> t.lexeme.equals(")"),
                    ")");

        } else {

            throw new SQLParseException(String.format("Invalid table on %d!",
                    pA.curTokenPos()));
        }

        analyseAlias(pA, children);

        return new Node(NodeType.TABLE, children);
    }

    public static Node analyseJoin(PushdownAutomaton pA) throws SQLParseException {

        List<Node> children = new ArrayList<>();

        switch (pA.curToken().lexeme) {

            case "JOIN" -> {

                Node res = new Node(NodeType.TERMINAL, pA.curToken());
                pA.getNextToken();
                return res;

            }

            case "INNER" -> pA.getNextToken();
            case "LEFT", "RIGHT" -> {

                pA.getNextToken();
                if (pA.curToken().lexeme.equals("OUTER")) {

                    pA.getNextToken();

                }

            }

            default -> throw new SQLParseException(String.format("Expected JOIN clause instead of %s on %d!",
                    pA.curToken(),
                    pA.curTokenPos()));

        }

        children.add(terminal(pA,
                t -> t.lexeme.equals("JOIN"),
                "JOIN"));

        return new Node(NodeType.JOIN, children);
    }

    public static Node analyseLogicalCondition(PushdownAutomaton pA,
                                               boolean isSubQuery) throws SQLParseException {

        List<Node> children = new ArrayList<>();

        switch (pA.curToken().lexeme) {

            case "USING" -> {

                children.add(terminal(pA,
                        t -> t.lexeme.equals("("),
                        "("));
                children.add(terminal(pA,
                        t -> t.category == Category.IDENTIFIER,
                        "Identifier"));
                children.add(terminal(pA,
                        t -> t.lexeme.equals(")"),
                        ")"));

            }

            case "ON" -> {

                pA.push(pA.curToken());
                LogicalConditionParser.analyseLogicalCondition(pA, children, isSubQuery);
                pA.pop();
            }

        }

        return new Node(NodeType.LOGICAL_CONDITION, children);
    }
}
