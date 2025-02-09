package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.interfaces.LambdaReleasable;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaProcessable;
import sql.to.mongodb.translator.interfaces.LambdaComparable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Parser {

    List<Token> tokens;
    int curTokenPos = 0;
    Token curToken;
    List<String> errors;

    Stack<Token> stack = new Stack<>();

    public Parser(List<Token> tokens, List<String> errors) {

        this.tokens = tokens;
        this.errors = errors;
    }

    void getNextToken() {

        if (curTokenPos != tokens.size()) {

            curToken = tokens.get(curTokenPos);
            ++curTokenPos;

        } else if (curToken.category != Category.UNDEFINED) {

            curToken = new Token("UNDEFINED", Category.UNDEFINED);

        }
    }

    void getPrevToken() {

        --curTokenPos;
        curToken = tokens.get(curTokenPos - 1);

    }

    public Node tryAnalyse() throws Exception {

        List<Node> children = new ArrayList<>();
        getNextToken();

        switch (curToken.lexeme) {

            case "SELECT" -> analyseSelect(children, false);

            default -> throw new Exception("Invalid query keyword!");
        }

        return new Node(NodeType.QUERY, children);
    }

    private void analyseSelect(List<Node> children, boolean isSubQuery) throws Exception {

        stack.push(new Token("0", Category.PROC_NUMBER));

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        if (curToken.lexeme.equals("DISTINCT")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        }

        List<Node> colNamesChildren = new ArrayList<>();
        children.add(ColumnNamesParser.analyseColumnNames(this, colNamesChildren));

        children.add(new Node(NodeType.TERMINAL, curToken));
        getNextToken();

        List<Node> tableNamesChildren = new ArrayList<>();
        children.add(TableNamesParser.analyseTableNames(this, tableNamesChildren, true, isSubQuery));

        if (curToken.lexeme.equals("WHERE")) {

            stack.push(curToken);

            children.add(new Node(NodeType.TERMINAL, curToken));

            List<Node> whereChildren = new ArrayList<>();
            LogicalConditionParser.analyseLogicalCondition(this, whereChildren, isSubQuery);
            children.add(new Node(NodeType.LOGICAL_CONDITION, whereChildren));

            stack.pop();
        }

        if (curToken.lexeme.equals("GROUP")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            children.add(terminal(t -> t.lexeme.equals("BY"), "BY"));

            List<Node> groupByChildren = new ArrayList<>();
            children.add(GroupByParser.analyseGroupBy(this, groupByChildren, isSubQuery));
        }

        if (curToken.lexeme.equals("HAVING")) {

            stack.push(curToken);

            children.add(new Node(NodeType.TERMINAL, curToken));

            List<Node> havingChildren = new ArrayList<>();
            LogicalConditionParser.analyseLogicalCondition(this, havingChildren, isSubQuery);
            children.add(new Node(NodeType.LOGICAL_CONDITION, havingChildren));

            stack.pop();
        }

        if (curToken.lexeme.equals("ORDER")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            children.add(terminal(t -> t.lexeme.equals("BY"), "BY"));

            List<Node> orderByChildren = new ArrayList<>();
            children.add(OrderByParser.analyseOrderBy(this, orderByChildren, isSubQuery));

        }

        if (isSubQuery && !curToken.lexeme.equals(")")) {

            getNextToken();

        }

    }

    void analyseSubquery(List<Node> children) throws Exception {

        getNextToken();
        List<Node> subqueryChildren = new ArrayList<>();
        analyseSelect(subqueryChildren, true);
        children.add(new Node(NodeType.QUERY, subqueryChildren));

    }

    void processColumnThroughStack(Token token) {

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

    void releaseColumnThroughStack() {

        Token prevToken = stack.pop();

        if (prevToken.category == Category.PROC_NUMBER) {

            int procNum = Integer.parseInt(prevToken.lexeme);
            --procNum;
            stack.push(new Token(Integer.toString(procNum), Category.PROC_NUMBER));

        } else {

            stack.push(new Token("0", Category.PROC_NUMBER));

        }
    }

    boolean analyseOperand(List<Node> children,
                                            LambdaProcessable processToken,
                                            LambdaComparable subQueryCheck,
                                            boolean isColumn) throws Exception {

        boolean isFound = true;

        if (curToken.category == Category.IDENTIFIER) {

            if (processToken != null) {

                processToken.execute(curToken);

            }

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (curToken.lexeme.equals(".")) {

                List<Node> identifierChildren = new ArrayList<>();
                identifierChildren.add(children.removeLast());

                getNextToken();
                identifierChildren.add(terminal(t -> t.category == Category.IDENTIFIER
                        || isColumn && t.category == Category.ALL, "Identifier or \"*\" in case of column"));

                children.add(new Node(NodeType.IDENTIFIER, identifierChildren));
            }

        } else if (curToken.category.equals(Category.NUMBER)
                || curToken.category.equals(Category.LITERAL)) {

            if (processToken != null) {

                processToken.execute(curToken);

            }

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        } else if (curToken.lexeme.equals("(")) {

            analyseSubquery(children);

            checkToken(t -> t.lexeme.equals(")"), ")");

            if (!subQueryCheck.execute(stack.peek())) {

                throw new Exception(String.format("Invalid subquery type on %d!", curTokenPos));

            }

        } else {

            isFound = false;

        }

        return isFound;
    }

    void analyseAlias(List<Node> children) throws Exception {

        if (curToken.lexeme.equals("AS")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER), "Identifier"));

        } else if (curToken.category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, new Token("AS" , Category.KEYWORD)));
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER), "Identifier"));

        } else if (children.getLast().getNodeType() == NodeType.QUERY) {

            throw new Exception(String.format("Subquery is missing elias on %d!", curTokenPos));

        }

    }

    void analyseArithmeticExpression(
            List<Node> children,
            boolean isColumn,
            LambdaProcessable processToken,
            LambdaReleasable releaseToken) throws Exception {

        List<Node> arithmeticChildren = new ArrayList<>();
        arithmeticChildren.add(children.getLast());

        analyseArithmeticRec(arithmeticChildren, isColumn);

        if (arithmeticChildren.size() > 1) {

            releaseToken.execute();
            processToken.execute(new Token("NON", Category.NUMBER));
            children.removeLast();
            children.add(new Node(NodeType.ARITHMETIC_EXP, arithmeticChildren));

        }
    }

    private void analyseArithmeticRec(List<Node> children, boolean isColumn) throws Exception {

        if (curToken.category == Category.ARITHMETIC_OPERATOR || curToken.category == Category.ALL) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (analyseOperand(children,
                    null,
                    t -> t.category != Category.PROC_NUMBER && t.category != Category.LITERAL,
                    isColumn)) {

                if (stack.peek().category == Category.LITERAL) {

                    throw new Exception(String.format("Literal is involved in arithmetic operations on %d!", curTokenPos));

                }

            } else if (curToken.category == Category.AGGREGATE) {

                FunctionsParser.analyseAggregate(this, children, isColumn);

            } else {

                throw new Exception(String.format("Invalid member of arithmetic operations on %d between %s and %s!",
                        curTokenPos,
                        curToken.lexeme,
                        tokens.get(curTokenPos)));

            }

            analyseArithmeticRec(children, isColumn);
        }
    }

    void checkToken(LambdaComparable comparator, String expectedToken) throws Exception {

        if (comparator.execute(curToken)) {

            getNextToken();

        } else {

            throw new Exception(String.format("%s expected instead of %s on %d!",
                    expectedToken,
                    curToken.lexeme,
                    curTokenPos));

        }
    }

    Node terminal(LambdaComparable comparator, String expectedToken) throws Exception {

        if (comparator.execute(curToken)) {

            Node terminalNode = new Node(NodeType.TERMINAL, curToken);
            getNextToken();
            return terminalNode;

        } else {

            throw new Exception(String.format("%s expected instead of %s on %d!",
                    expectedToken,
                    curToken.lexeme,
                    curTokenPos));

        }
    }
}
