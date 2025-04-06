package sql.to.mongodb.translator.service.parser.dml;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.*;
import sql.to.mongodb.translator.service.parser.main.cases.*;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.main.cases.LogicalConditionParser.analyseLogicalCondition;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.checkToken;

public class SelectParser {

    public static void analyseSelect(PushdownAutomaton pA,
                                     List<Node> children,
                                     boolean isSubQuery) throws SQLParseException {

        pA.push(new Token("0", Category.PROC_NUMBER));

        children.add(new Node(NodeType.TERMINAL, pA.curToken()));
        pA.getNextToken();

        if (pA.curToken().lexeme.equals("DISTINCT")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        }

        List<Node> colNamesChildren = new ArrayList<>();
        children.add(ColumnNamesParser.analyseColumnNames(pA, colNamesChildren));

        children.add(new Node(NodeType.TERMINAL, pA.curToken()));
        pA.getNextToken();

        List<Node> tableNamesChildren = new ArrayList<>();

        children.add(TableNamesParser.analyseTableNames(pA,
                tableNamesChildren,
                true,
                isSubQuery));

        if (pA.curToken().lexeme.equals("WHERE")) {

            analyseLogicalPart(pA, children, isSubQuery);

        }

        if (pA.curToken().lexeme.equals("GROUP")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();
            checkToken(pA,
                    t -> t.lexeme.equals("BY"), "BY");

            List<Node> groupByChildren = new ArrayList<>();
            children.add(GroupByParser.analyseGroupBy(pA, groupByChildren, isSubQuery));
        }

        if (pA.curToken().lexeme.equals("HAVING")) {

            analyseLogicalPart(pA, children, isSubQuery);

        }

        if (pA.curToken().lexeme.equals("ORDER")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();
            checkToken(pA,
                    t -> t.lexeme.equals("BY"), "BY");

            List<Node> orderByChildren = new ArrayList<>();
            children.add(OrderByParser.analyseOrderBy(pA, orderByChildren, isSubQuery));

        }

        if (pA.curToken().lexeme.equals("LIMIT")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

            if (pA.curToken().category == Category.NUMBER) {

                int num = Integer.parseInt(pA.curToken().lexeme);

                if (num <= 0) {

                    throw new SQLParseException(String.format("Invalid number in \"LIMIT\" on %d!",
                            pA.curTokenPos()));

                }

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

            } else {

                throw new SQLParseException(String.format("Invalid member of \"LIMIT\" on %d!",
                        pA.curTokenPos()));

            }
        }

        if (pA.curToken().lexeme.equals("OFFSET")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

            if (pA.curToken().category == Category.NUMBER) {

                int num = Integer.parseInt(pA.curToken().lexeme);

                if (num <= 0) {

                    throw new SQLParseException(String.format("Invalid number in \"OFFSET\" on %d!",
                            pA.curTokenPos()));

                }

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

            } else {

                throw new SQLParseException(String.format("Invalid member of \"OFFSET\" on %d!",
                        pA.curTokenPos()));

            }
        }

        if (isSubQuery && !pA.curToken().lexeme.equals(")")) {

            pA.getNextToken();

        }

    }

    public static void analyseSubquery(PushdownAutomaton pA,
                                       List<Node> children) throws SQLParseException {

        pA.getNextToken();
        List<Node> subqueryChildren = new ArrayList<>();
        analyseSelect(pA, subqueryChildren, true);
        children.add(new Node(NodeType.QUERY, subqueryChildren));

    }

    private static void analyseLogicalPart(PushdownAutomaton pA,
                                           List<Node> children,
                                           boolean isSubQuery) throws SQLParseException {

        pA.push(pA.curToken());

        children.add(new Node(NodeType.TERMINAL, pA.curToken()));

        List<Node> logicalPartChildren = new ArrayList<>();

        analyseLogicalCondition(pA,
                logicalPartChildren,
                isSubQuery);

        children.add(new Node(NodeType.LOGICAL_CONDITION, logicalPartChildren));

        pA.pop();

    }

}
