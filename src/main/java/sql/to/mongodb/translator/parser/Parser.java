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

/*        switch (curToken) {
            case new Token("SELECT", Category.DML):
                children.add(terminal(t -> true));
                children.add(analyseColumnNames(0));
                children.add(terminal(t -> t.category.equals(Category.KEYWORD) && t.lexeme.equals("FROM")));
                children.add(terminal(t -> t.category.equals(Category.IDENTIFIER)));
*//*                children.add(where_part());
                children.add(skip_limit_part());*//*
                break;
            default:
                // throw new Exception("Wrong first of query", curTokenPos);
        }*/

/*        if (lexer.getCurrentToken() != Token.END) {
            throw new Exception("Wrong follow of query", curTokenPos);
        }*/
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

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();
/*        children.add(terminal(t -> t.category.equals(Category.KEYWORD) && t.lexeme.equals("FROM")));
        children.add(terminal(t -> t.category.equals(Category.IDENTIFIER)));*/
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
