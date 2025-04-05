package sql.to.mongodb.translator.service.parser;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.List;
import java.util.Stack;

public class PushdownAutomaton {

    private List<Token> tokens;
    private int curTokenPos;
    private Token curToken;
    private Stack<Token> stack;

    public PushdownAutomaton(List<Token> tokens) {

        this.tokens = tokens;
        curTokenPos = 0;
        stack = new Stack<>();

    }

    public int curTokenPos() {
        return curTokenPos;
    }

    public Token curToken() {
        return curToken;
    }

    public Token token(int i) {
        return tokens.get(i);
    }

    public void getNextToken() {

        if (curTokenPos != tokens.size()) {

            curToken = tokens.get(curTokenPos);
            ++curTokenPos;

        } else if (curToken.category != Category.UNDEFINED) {

            curToken = new Token("UNDEFINED", Category.UNDEFINED);

        }

    }

    public void getPrevToken() {

        --curTokenPos;
        curToken = tokens.get(curTokenPos - 1);

    }

    public boolean isEnd() {

        return curTokenPos == tokens.size();

    }

    public void push(Token token) {
        stack.push(token);
    }

    public Token pop() {
        return stack.pop();
    }

    public Token peek() {
        return stack.peek();
    }
}
