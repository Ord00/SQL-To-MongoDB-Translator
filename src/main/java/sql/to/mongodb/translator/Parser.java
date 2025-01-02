package sql.to.mongodb.translator;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaComparable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

public class Parser {

    private List<Token> tokens;
    private int curTokenPos = 0;
    private Token curToken;
    private List<String> errors;

    private Stack<Token> stack = new Stack<>();

    public Parser(List<Token> tokens, List<String> errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    private void getNextToken() {
        curToken = tokens.get(curTokenPos);
        ++curTokenPos;
    }

    public Node tryAnalyse() throws Exception {

        List<Node> children = new ArrayList<>();
        getNextToken();

        if (curToken.equals(new Token("SELECT", Category.DML))) {
            children.add(terminal(t -> true));
            children.add(analyseColumnNames(0));
            children.add(terminal(t -> t.category.equals(Category.KEYWORD) && t.lexeme.equals("FROM")));
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER)));
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

    private Node analyseColumnNames(int cntCols) throws Exception {

        List<Node> children = new ArrayList<>();
        getNextToken();

        while (!stack.empty()) {
            Token token = stack.pop();
            if (!curToken.equals(token)) {
                throw new Exception("mistake!");
            }
            getNextToken();
        }

        if (cntCols != 0) {
            children.add(terminal(t -> t.category.equals(Category.PUNCTUATION) && t.lexeme.equals(",")));
        }

        if (curToken.category == Category.IDENTIFIER) {
            children.add(terminal(t -> true));
            children.add(analyseColumnNames(cntCols + 1));
            return new Node(NodeType.COLUMN_NAMES, children);
        }

        if (curToken.category == Category.ALL) {
            children.add(terminal(t -> true));
            children.add(analyseColumnNames(cntCols + 1));
            return new Node(NodeType.COLUMN_NAMES, children);
        }

        if (curToken.category.equals(Category.KEYWORD)
                && curToken.lexeme.equals("FROM") // + проверка на терминальность последнего
            ) {

        }

        // throw new Exception("Wrong first of column_names", curTokenPos);

        return new Node(NodeType.COLUMN_NAMES, children);
    }

    private Node terminal(LambdaComparable comparator) throws Exception {
        if (comparator.execute(curToken)) {
            Node terminalNode = new Node(NodeType.TERMINAL, curToken);
            getNextToken();
            return terminalNode;
        } else {
            throw new Exception(curToken + " expected instead of " + curToken);
        }
    }
}
