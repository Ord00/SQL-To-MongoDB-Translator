package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaComparable;

import java.util.ArrayList;
import java.util.List;

public class LogicalConditionParser extends Parser {
    public  LogicalConditionParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseLogicalCondition(List<Node> children) throws Exception {
        if (analyseOperand(children)) {
            children.add(analyseOperation());
        } else if (curToken.lexeme.equals("NOT")) {
            getNextToken();
            children.add(terminal(t -> t.lexeme.equals("EXISTS")));
            children.add(terminal(t -> t.lexeme.equals("(")));
            children.add(tryAnalyse(true));
        } else if (curToken.lexeme.equals("EXISTS")) {
            getNextToken();
            children.add(terminal(t -> t.lexeme.equals("(")));
            children.add(tryAnalyse(true));
        } else {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        if (curToken.category == Category.LOGICAL_COMBINE) {
            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            return analyseLogicalCondition(children);
        } else if (curTokenPos == tokens.size() - 1 || curToken.category == Category.KEYWORD) {
            return new Node(NodeType.LOGICAL_CONDITION, children);
        } else {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }
    }

    public static boolean analyseOperand(List<Node> children) throws Exception {

        boolean isFound = true;

        if (curToken.category == Category.IDENTIFIER
                || curToken.category.equals(Category.NUMBER)
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

    public static void analyseLogicalOperator(List<Node> children, LambdaComparable comparator) throws Exception {

        stack.push(curToken);

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        if (!stack.pop().lexeme.equals("=") && curToken.category == Category.LOGICAL_OPERATOR) {
            children.add(terminal(comparator));
        }

        if (!analyseOperand(children)) {
            if (curToken.lexeme.equals("ANY") || curToken.lexeme.equals("SOME") || curToken.lexeme.equals("ALL")) {

                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

                children.add(terminal(t -> t.lexeme.equals("(")));
                children.add(tryAnalyse(true));
            } else {
                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
            }
        }
    }

    public static void analyseLike(List<Node> children) throws Exception {
        Token operandToken = stack.pop();
        if (operandToken.category != Category.IDENTIFIER
                && operandToken.category != Category.LITERAL) {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        if (!analyseOperand(children) || curToken.category == Category.NUMBER) {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }
    }

    public static Node analyseIn(List<Node> children, boolean isCheckStack) throws Exception {

        boolean newIsCheckStack = isCheckStack;

        if (!analyseOperand(children)) {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        Token curAttribute = stack.pop();

        if (isCheckStack) {

            Token prevAttribute = stack.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
            && curAttribute.category != prevAttribute.category) {
                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
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

    public static void analyseBetween(List<Node> children) throws Exception {

        if (!analyseOperand(children)) {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        stack.pop();

        children.add(terminal(t -> t.lexeme.equals("AND")));
        getNextToken();

        if (!analyseOperand(children)) {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        stack.pop();
    }

    public static Node analyseOperation() throws Exception {

        List<Node> children = new ArrayList<>();

        if (curToken.lexeme.equals("=")) {
            analyseLogicalOperator(children, t -> true);
        } else if (curToken.lexeme.equals("<")) {
            analyseLogicalOperator(children, t -> t.lexeme.equals(">") || t.lexeme.equals("="));
        } else if (curToken.lexeme.equals(">")) {
            analyseLogicalOperator(children, t -> t.lexeme.equals("="));
        } else if (curToken.lexeme.equals("IS")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (curToken.lexeme.equals("NOT")) {
                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();
            }

            children.add(terminal(t -> t.category == Category.NULL));

        } else if (curToken.lexeme.equals("NOT")) {
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
                    children.add(terminal(t -> t.lexeme.equals("(")));
                    List<Node> inChildren = new ArrayList<>();
                    analyseIn(inChildren, false);
                }
                case "BETWEEN" -> analyseBetween(children);
                default -> throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
            }
        } else if (curToken.lexeme.equals("LIKE")) {
            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            analyseLike(children);
        } else if (curToken.lexeme.equals("IN")) {
            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            children.add(terminal(t -> t.lexeme.equals("(")));
            List<Node> inChildren = new ArrayList<>();
            analyseIn(inChildren, false);
        } else if (curToken.lexeme.equals("BETWEEN")) {
            analyseBetween(children);
        } else {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

        return new Node(NodeType.LOGICAL_CONDITION, children);
    }
}
