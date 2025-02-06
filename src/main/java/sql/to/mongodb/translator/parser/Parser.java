package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaCallable;
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
        if (curTokenPos != tokens.size()) {
            curToken = tokens.get(curTokenPos);
            ++curTokenPos;
        } else if (curToken.category != Category.UNDEFINED) {
            curToken = new Token("UNDEFINED", Category.UNDEFINED);
        }
    }

    public static Node tryAnalyse(boolean isSubQuery) throws Exception {

        List<Node> children = new ArrayList<>();
        getNextToken();

        if (isSubQuery && curToken.lexeme.equals("SELECT")) {

            stack.push(new Token("0", Category.PROC_NUMBER));
            analyseSelect(children);

        } else if (!isSubQuery) {

            switch (curToken.lexeme) {

                case "SELECT" -> {

                    stack.push(new Token("0", Category.PROC_NUMBER));
                    analyseSelect(children);

                }

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

    protected static void processOperandThroughStack(Token token) {

        Token prevToken = stack.pop();

        if (prevToken.category == Category.PROC_NUMBER) {

            if (prevToken.lexeme.equals("0")) {

                stack.push(token);

            } else {

                int procNum = Integer.parseInt(prevToken.lexeme);
                procNum++;
                stack.push(new Token(Integer.toString(procNum), Category.PROC_NUMBER));

            }

        } else {

            stack.push(new Token("2", Category.PROC_NUMBER));

        }
    }

    // !!! добавить параметр - лямбду для проверки соответствия подзапроса некому условию
    protected static boolean analyseOperand(List<Node> children,
                                            LambdaCallable func,
                                            LambdaComparable subQueryCheck) throws Exception {

        boolean isFound = true;

        if (curToken.category == Category.IDENTIFIER) {

            if (func != null) {

                func.execute(curToken);

            }

            List<Node> identifierChildren = new ArrayList<>();
            identifierChildren.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (curToken.lexeme.equals(".")) {

                getNextToken();
                identifierChildren.add(terminal(t -> t.category == Category.IDENTIFIER));

            }

            children.add(new Node(NodeType.IDENTIFIER, identifierChildren));

        } else if (curToken.category.equals(Category.NUMBER)
                || curToken.category.equals(Category.LITERAL)) {

            if (func != null) {

                func.execute(curToken);

            }

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (curToken.lexeme.equals("(")) {

            children.add(tryAnalyse(true));

            if (!subQueryCheck.execute(stack.peek())) {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            }

        } else {

            isFound = false;

        }

        return isFound;
    }

    protected static void analyseAlias(List<Node> children) throws Exception {

        if (curToken.lexeme.equals("AS")) {

            getNextToken();
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER)));

        } else if (curToken.category.equals(Category.IDENTIFIER)) {

            getNextToken();

        } else if (children.getLast().getNodeType() == NodeType.QUERY) {

            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }

    }

    protected static void analyseArithmeticExpression(List<Node> children, boolean isColumn, LambdaCallable func) throws Exception {

        List<Node> arithmeticChildren = new ArrayList<>();
        arithmeticChildren.add(children.getLast());

        analyseArithmeticRec(arithmeticChildren, isColumn);

        if (arithmeticChildren.size() > 1) {

            func.execute(new Token("1", Category.NUMBER));
            children.removeLast();
            children.add(new Node(NodeType.ARITHMETIC_EXP, arithmeticChildren));

        }
    }

    protected static void analyseArithmeticRec(List<Node> children, boolean isColumn) throws Exception {

        if (curToken.category == Category.ARITHMETIC_OPERATOR) {

            children.add(new Node(NodeType.TERMINAL, curToken));

            if (analyseOperand(children,
                    null,
                    t -> t.category != Category.PROC_NUMBER && t.category != Category.LITERAL)) {

                if (stack.peek().category == Category.LITERAL) {

                    throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

                }
            } else if (curToken.category == Category.AGGREGATE) {

                FunctionsParser.analyseAggregate(children, isColumn);

            } else {

                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));

            }
        }
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
