package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public class OrderByParser extends Parser {

    public OrderByParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseOrderBy(List<Node> children, boolean isSubQuery) throws Exception {

        if (analyseOperand(children,
                t -> stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            analyseArithmeticExpression(children, true, t -> stack.push(t), () -> stack.pop());

            Token token = stack.pop();

            if (token.category == Category.LITERAL) {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            } else if (token.category == Category.NUMBER) {

                int curNum = Integer.parseInt(token.lexeme);

                if (curNum <= 0 || Integer.parseInt(stack.peek().lexeme) < curNum) {

                    throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

                }
            }

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        if (curToken.lexeme.equals("DESC") || curToken.lexeme.equals("ASC")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else {

            children.add(new Node(NodeType.TERMINAL, new Token("ASC", Category.KEYWORD)));

        }

        if (curToken.lexeme.equals(",")) {

            getNextToken();
            return analyseOrderBy(children, isSubQuery);

        } else if (curTokenPos == tokens.size() || isSubQuery && curToken.lexeme.equals(")")) {

            return new Node(NodeType.ORDER_BY, children);

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }
    }
}
