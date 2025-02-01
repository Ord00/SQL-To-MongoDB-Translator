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

    protected Stack<Token> stack = new Stack<>();

    public Parser(List<Token> tokens, List<String> errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    protected static void getNextToken() {
        curToken = tokens.get(curTokenPos);
        ++curTokenPos;
    }

    public Node tryAnalyse() throws Exception {

        List<Node> children = new ArrayList<>();
        getNextToken();

        if (curToken.lexeme.equals("SELECT")) {
            analyseSelect(children);
/*                children.add(where_part());
                children.add(skip_limit_part());*/
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

        children.add(terminal(t -> true, NodeType.TERMINAL));

        List<Node> colNamesChildren = new ArrayList<>();
        children.add(ColumnNamesParser.analyseColumnNames(colNamesChildren));


/*        children.add(terminal(t -> t.category.equals(Category.KEYWORD) && t.lexeme.equals("FROM")));
        children.add(terminal(t -> t.category.equals(Category.IDENTIFIER)));*/
    }

    protected static Node terminal(LambdaComparable comparator, NodeType nodeType) throws Exception {
        if (comparator.execute(curToken)) {
            Node terminalNode = new Node(nodeType, curToken);
            getNextToken();
            return terminalNode;
        } else {
            throw new Exception(curToken + " expected instead of " + curToken);
        }
    }
}
