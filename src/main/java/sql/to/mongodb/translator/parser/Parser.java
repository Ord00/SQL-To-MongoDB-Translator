package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaComparable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Parser {

    protected static List<Token> tokens;
    protected static int curTokenPos = 0;
    protected static Token curToken;
    protected List<String> errors;

    protected static Stack<Token> stack = new Stack<>();

    public Parser(List<Token> tokens, List<String> errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    protected static void getNextToken() {
        curToken = tokens.get(curTokenPos);
        ++curTokenPos;
    }

    public static Node tryAnalyse(boolean isSubQuery) throws Exception {

        List<Node> children = new ArrayList<>();
        getNextToken();

        if (isSubQuery && curToken.lexeme.equals("SELECT")) {
            stack.push(curToken);
            analyseSelect(children);
        } else if (!isSubQuery) {

            stack.push(curToken);

            switch (curToken.lexeme) {

                case "SELECT" -> analyseSelect(children);
                default -> throw new Exception("Wrong first of query");
            }
        }

        return new Node(NodeType.QUERY, children);
    }

    private static void analyseSelect(List<Node> children ) throws Exception {

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        List<Node> colNamesChildren = new ArrayList<>();
        children.add(ColumnNamesParser.analyseColumnNames(colNamesChildren));

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        List<Node> tableNamesChildren = new ArrayList<>();
        children.add(TableNamesParser.analyseTableNames(tableNamesChildren, true));

        if (curToken.lexeme.equals("WHERE")) {

            children.add(new Node(NodeType.TERMINAL, curToken));

            List<Node> whereChildren = new ArrayList<>();
            LogicalConditionParser.analyseLogicalCondition(whereChildren);
            children.add(new Node(NodeType.LOGICAL_CONDITION, whereChildren));
        }

        if (curToken.lexeme.equals("GROUP")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            children.add(terminal(t -> t.lexeme.equals("BY")));

            List<Node> groupByChildren = new ArrayList<>();
            children.add(GroupByParser.analyseGroupBy(groupByChildren));
        }

        if (curToken.lexeme.equals("HAVING")) {

            children.add(new Node(NodeType.TERMINAL, curToken));

            List<Node> havingChildren = new ArrayList<>();
            LogicalConditionParser.analyseLogicalCondition(havingChildren);
            children.add(new Node(NodeType.LOGICAL_CONDITION, havingChildren));
        }

        if (curToken.lexeme.equals("ORDER")) {

        }

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();
    }

    protected static boolean analyseOperand(List<Node> children) throws Exception {

        boolean isFound = true;

        if (curToken.category == Category.IDENTIFIER) {

            List<Node> identifierChildren = new ArrayList<>();
            stack.push(curToken);
            identifierChildren.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (curToken.lexeme.equals(".")) {

                getNextToken();
                identifierChildren.add(terminal(t -> t.category == Category.IDENTIFIER));

            }

            children.add(new Node(NodeType.IDENTIFIER, identifierChildren));

        } else if (curToken.category.equals(Category.NUMBER)
                || curToken.category.equals(Category.LITERAL)) {

            stack.push(curToken);
            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (curToken.lexeme.equals("(")) {

            stack.push(new Token("SUBQUERY", Category.IDENTIFIER));
            children.add(tryAnalyse(true));

        } else {

            isFound = false;

        }

        return isFound;
    }

    protected static Node terminal(LambdaComparable comparator) throws Exception {
        if (comparator.execute(curToken)) {
            Node terminalNode = new Node(NodeType.TERMINAL, curToken);
            getNextToken();
            return terminalNode;
        } else {
            throw new Exception(curToken + " expected instead of " + curToken);
        }
    }
}
