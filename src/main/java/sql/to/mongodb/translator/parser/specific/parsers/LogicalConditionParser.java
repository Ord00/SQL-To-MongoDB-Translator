package sql.to.mongodb.translator.parser.specific.parsers;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaComparable;

import java.util.ArrayList;
import java.util.List;

public class LogicalConditionParser extends Parser {

    public  LogicalConditionParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static void analyseLogicalCondition(List<Node> children, boolean isSubQuery) throws Exception {

        List<Node> logicalCheckChildren = new ArrayList<>();

        getNextToken();

        while (curToken.lexeme.equals("(")) {

            stack.push(curToken);
            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        }

        if (curToken.lexeme.equals("SELECT")) {

            stack.pop();
            children.removeLast();
            getPrevToken();

        }

        if (analyseOperand(logicalCheckChildren,
                t -> stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            analyseArithmeticExpression(logicalCheckChildren,
                    false,
                    t -> stack.push(t),
                    () -> stack.pop());

            analyseOperation(logicalCheckChildren);

        } else if (curToken.lexeme.equals("NOT")) {

            getNextToken();
            logicalCheckChildren.add(terminal(t -> t.lexeme.equals("EXISTS"), "EXISTS"));

            if (!curToken.lexeme.equals("(")) {

                throw new Exception(String.format("Expected \"(\" after EXISTS on %d!", curTokenPos));

            }

            logicalCheckChildren.add(tryAnalyse(true));

            checkToken(t -> t.lexeme.equals(")"), ")");

        } else if (curToken.lexeme.equals("EXISTS")) {

            getNextToken();
            logicalCheckChildren.add(terminal(t -> t.lexeme.equals("("), "("));
            logicalCheckChildren.add(tryAnalyse(true));

        } else if (curToken.category == Category.AGGREGATE) {

            FunctionsParser.analyseAggregate(logicalCheckChildren, false);

            analyseArithmeticExpression(logicalCheckChildren,
                    false,
                    t -> stack.push(t),
                    () -> stack.pop());

            analyseOperation(logicalCheckChildren);

        } else {

            throw new Exception(String.format("Invalid left operand of logical expression on %d!", curTokenPos));

        }

        children.add(new Node(NodeType.LOGICAL_CHECK, logicalCheckChildren));

        clearLogicalExpInStack();

        Token bracketToken = stack.peek();

        while (curToken.lexeme.equals(")") && bracketToken.lexeme.equals("(")) {

            stack.pop();
            bracketToken = stack.peek();

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        }

        if (!isSubQuery && curToken.lexeme.equals(")")) {

            throw new Exception(String.format("Invalid brackets of logical expression on %d!", curTokenPos));

        }

        if (curToken.category == Category.LOGICAL_COMBINE) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            analyseLogicalCondition(children, isSubQuery);

        } else if (curTokenPos == tokens.size() || curToken.category == Category.KEYWORD) {

            if (stack.peek().lexeme.equals("(")) {

                throw new Exception(String.format("Invalid brackets of logical expression on %d!", curTokenPos));

            }

        } else if (!(isSubQuery && curToken.lexeme.equals(")"))) {

            throw new Exception(String.format("Invalid link between logical expressions on %d!", curTokenPos));

        }
    }

    public static void analyseLogicalOperator(List<Node> children,
                                              LambdaComparable comparator,
                                              String expectedToken) throws Exception {

        stack.push(curToken);

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        if (!stack.pop().lexeme.equals("=") && curToken.category == Category.LOGICAL_OPERATOR) {

            children.add(terminal(comparator, expectedToken));

        }

        if (!analyseOperand(children,
                t -> stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            if (curToken.lexeme.equals("ANY") || curToken.lexeme.equals("SOME") || curToken.lexeme.equals("ALL")) {

                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

                if (!curToken.lexeme.equals("(")) {

                    throw new Exception(String.format("Expected \"(\" after %s on %d",
                            tokens.get(curTokenPos - 2),
                            curTokenPos));

                }

                children.add(tryAnalyse(true));

                checkToken(t -> t.lexeme.equals(")"), ")");

                if (stack.peek().category == Category.PROC_NUMBER) {

                    throw new Exception(String.format("Invalid type of subquery on %d!", curTokenPos));

                }

            } else {

                throw new Exception(String.format("Expected %s instead of %s on %d",
                        "[ANY, SOME, ALL]",
                        curToken.lexeme,
                        curTokenPos));

            }
        }
    }

    public static void analyseLike(List<Node> children) throws Exception {

        Token operandToken = stack.pop();

        if (operandToken.category != Category.IDENTIFIER
                && operandToken.category != Category.LITERAL) {

            throw new Exception(String.format("Invalid left operand of LIKE on %d!", curTokenPos));

        }

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        if (!analyseOperand(children,
                t -> stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)
                || stack.pop().category == Category.NUMBER) {

            throw new Exception(String.format("Invalid right operand of LIKE on on %d!", curTokenPos));

        }
    }

    public static Node analyseIn(List<Node> children, boolean isCheckStack) throws Exception {

        boolean newIsCheckStack = isCheckStack;

        if (!analyseOperand(children,
                t -> stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new Exception(String.format("Invalid member of IN on %d!", curTokenPos));

        }

        Token curAttribute = stack.pop();

        if (curAttribute.category == Category.AGGREGATE) {

            throw new Exception(String.format("Aggregate function in IN on %d!", curTokenPos));

        }

        if (isCheckStack) {

            Token prevAttribute = stack.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
            && curAttribute.category != prevAttribute.category) {

                throw new Exception(String.format("Invalid attribute of IN on %d!", curTokenPos));

            }

        } else if (curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL) {

            stack.push(curAttribute);
            newIsCheckStack = true;
            
        }

        return switch (curToken.lexeme) {

            case "," -> {

                getNextToken();
                yield analyseIn(children, newIsCheckStack);

            }

            case ")" -> {

                if (isCheckStack) {
                    stack.pop();
                }
                yield new Node(NodeType.ATTRIBUTES, children);

            }

            default -> throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        };
    }

    public static boolean analyseInOfSubquery(List<Node> children, LambdaComparable subQueryCheck) throws Exception {

        boolean isFound = true;

        if (curToken.lexeme.equals("SELECT")) {

            getPrevToken();
            Node subQueryIn = tryAnalyse(true);

            if (!subQueryCheck.execute(stack.peek())) {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            } else if (curToken.lexeme.equals(")")) {

                children.add(new Node(NodeType.ATTRIBUTES, List.of(subQueryIn)));
                getNextToken();
            }

        } else {

            isFound = false;

        }

        return isFound;

    }

    public static void analyseBetween(List<Node> children) throws Exception {

        if (!analyseOperand(children,
                null,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }


        children.add(terminal(t -> t.lexeme.equals("AND"), "AND"));
        getNextToken();

        if (!analyseOperand(children,
                null,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

    }

    public static void analyseOperation(List<Node> children) throws Exception {

        switch (curToken.lexeme) {

            case "=" -> analyseLogicalOperator(children,
                    t -> true,
                    "");

            case "<" -> analyseLogicalOperator(children,
                    t -> t.lexeme.equals(">") || t.lexeme.equals("="),
                    "[>, =]");

            case ">" -> analyseLogicalOperator(children,
                    t -> t.lexeme.equals("="),
                    "=");

            case "IS" -> {

                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

                if (curToken.lexeme.equals("NOT")) {

                    children.add(new Node(NodeType.TERMINAL, curToken));
                    getNextToken();

                }

                children.add(terminal(t -> t.category == Category.NULL, "NULL"));

            }
            case "NOT" -> {
                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

                switch (curToken.lexeme) {
                    case "LIKE" -> {
                        children.add(new Node(NodeType.TERMINAL, curToken));
                        getNextToken();
                        analyseLike(children);
                    }
                    case "IN" -> {
                        children.add(new Node(NodeType.TERMINAL, curToken));
                        getNextToken();
                        checkToken(t -> t.lexeme.equals("("), "(");

                        if (!analyseInOfSubquery(children, t -> t.category != Category.PROC_NUMBER)) {

                            List<Node> inChildren = new ArrayList<>();
                            analyseIn(inChildren, false);

                        }
                    }
                    case "BETWEEN" -> analyseBetween(children);
                    default -> throw new Exception(String.format("%s expected instead of %s on %d!",
                            "[LIKE, IN, BETWEEN]",
                            curToken.lexeme,
                            curTokenPos));
                }
            }
            case "LIKE" -> {
                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();
                analyseLike(children);
            }
            case "IN" -> {
                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();
                checkToken(t -> t.lexeme.equals("("), "(");

                if (!analyseInOfSubquery(children, t -> t.category != Category.PROC_NUMBER)) {

                    List<Node> inChildren = new ArrayList<>();
                    analyseIn(inChildren, false);

                }
            }
            case "BETWEEN" -> analyseBetween(children);
            default -> throw new Exception(String.format("Invalid logical operation on %d!", curTokenPos));
        }
    }

    public static void clearLogicalExpInStack() {

        Token curStackToken = stack.peek();

        while (curStackToken.category != Category.KEYWORD && !curStackToken.lexeme.equals("(")) {

            stack.pop();
            curStackToken = stack.peek();

        }

    }
}
