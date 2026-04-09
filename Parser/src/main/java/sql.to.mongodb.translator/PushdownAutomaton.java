package sql.to.mongodb.translator;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.scanner.Category;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;
import java.util.Stack;

@Component
public class PushdownAutomaton {

    private List<Token> tokens;
    private int curTokenPos;
    private Token curToken;
    private final Stack<Token> stack;

    public PushdownAutomaton() {
        stack = new Stack<>();
    }

    public void init(List<Token> tokens) {

        this.tokens = tokens;
        curTokenPos = 0;
        stack.clear();

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
