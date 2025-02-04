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

    public static void analyseLogicalCondition(List<Node> children) throws Exception {

        List<Node> logicalCheckChildren = new ArrayList<>();

        getNextToken();

        while (curToken.lexeme.equals("(")) {

            stack.push(curToken);
            logicalCheckChildren.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        }

        if (analyseOperand(logicalCheckChildren)) {

           analyseOperation(logicalCheckChildren);

        } else if (curToken.lexeme.equals("NOT")) {

            getNextToken();
            logicalCheckChildren.add(terminal(t -> t.lexeme.equals("EXISTS")));
            logicalCheckChildren.add(terminal(t -> t.lexeme.equals("(")));
            logicalCheckChildren.add(tryAnalyse(true));

        } else if (curToken.lexeme.equals("EXISTS")) {

            getNextToken();
            logicalCheckChildren.add(terminal(t -> t.lexeme.equals("(")));
            logicalCheckChildren.add(tryAnalyse(true));

        } else if (curToken.category == Category.AGGREGATE) {

            FunctionsParser.analyseAggregate(logicalCheckChildren, false);

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        children.add(new Node(NodeType.LOGICAL_CHECK, logicalCheckChildren));

        Token bracketToken = stack.peek();

        while (curToken.lexeme.equals(")") && bracketToken.lexeme.equals("(")) {

            stack.pop();
            bracketToken = stack.peek();

            logicalCheckChildren.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        }

        if (curToken.lexeme.equals(")")) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

        if (curToken.category == Category.LOGICAL_COMBINE) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            analyseLogicalCondition(children);

        } else if (curTokenPos == tokens.size() - 1 || curToken.category == Category.KEYWORD) {

            if (stack.peek().lexeme.equals("(")) {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            }

        } else {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }
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

        stack.pop();
        stack.pop();
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

        if (curAttribute.category == Category.AGGREGATE) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

        }

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

    public static void analyseOperation(List<Node> children) throws Exception {

        switch (curToken.lexeme) {
            case "=" -> analyseLogicalOperator(children, t -> true);
            case "<" -> analyseLogicalOperator(children, t -> t.lexeme.equals(">") || t.lexeme.equals("="));
            case ">" -> analyseLogicalOperator(children, t -> t.lexeme.equals("="));
            case "IS" -> {

                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

                if (curToken.lexeme.equals("NOT")) {

                    children.add(new Node(NodeType.TERMINAL, curToken));
                    getNextToken();

                }

                children.add(terminal(t -> t.category == Category.NULL));

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
                        children.add(terminal(t -> t.lexeme.equals("(")));
                        List<Node> inChildren = new ArrayList<>();
                        analyseIn(inChildren, false);
                    }
                    case "BETWEEN" -> analyseBetween(children);
                    default -> throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
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
                children.add(terminal(t -> t.lexeme.equals("(")));
                List<Node> inChildren = new ArrayList<>();
                analyseIn(inChildren, false);
            }
            case "BETWEEN" -> analyseBetween(children);
            default -> throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }
    }
}
