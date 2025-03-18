package sql.to.mongodb.translator.parser;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.exceptions.SQLScanException;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.TokenProcessable;
import sql.to.mongodb.translator.interfaces.TokenComparable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@Component
public class Parser {

    List<Token> tokens;
    int curTokenPos = 0;
    Token curToken;
    List<String> errors;

    Stack<Token> stack = new Stack<>();

    public Parser(List<Token> tokens, List<String> errors) {

        this.tokens = tokens;
        this.errors =  errors;

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

    public Node tryAnalyse() throws SQLParseException, SQLScanException {

        for (String error : errors) {

            throw new SQLScanException(error);
        }

        List<Node> children = new ArrayList<>();
        getNextToken();

        switch (curToken.lexeme) {

            case "SELECT" -> analyseSelect(children, false);

            default -> throw new SQLParseException("Invalid query keyword!");
        }

        if (curTokenPos != tokens.size()) {

            throw new SQLParseException(String.format("Expected end of query on %d!",
                    curTokenPos));
        }

        return new Node(NodeType.QUERY, children);
    }

    private void analyseSelect(List<Node> children,
                               boolean isSubQuery) throws SQLParseException {

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

        children.add(TableNamesParser.analyseTableNames(this,
                tableNamesChildren,
                true,
                isSubQuery));

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
            checkToken(t -> t.lexeme.equals("BY"), "BY");

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
            checkToken(t -> t.lexeme.equals("BY"), "BY");

            List<Node> orderByChildren = new ArrayList<>();
            children.add(OrderByParser.analyseOrderBy(this, orderByChildren, isSubQuery));

        }

        if (curToken.lexeme.equals("LIMIT")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (curToken.category == Category.NUMBER) {

                int num = Integer.parseInt(curToken.lexeme);

                if (num <= 0) {

                    throw new SQLParseException(String.format("Invalid number in \"LIMIT\" on %d!",
                            curTokenPos));

                }

                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

            } else {

                throw new SQLParseException(String.format("Invalid member of \"LIMIT\" on %d!",
                        curTokenPos));

            }
        }

        if (curToken.lexeme.equals("OFFSET")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

            if (curToken.category == Category.NUMBER) {

                int num = Integer.parseInt(curToken.lexeme);

                if (num <= 0) {

                    throw new SQLParseException(String.format("Invalid number in \"OFFSET\" on %d!",
                            curTokenPos));

                }

                children.add(new Node(NodeType.TERMINAL, curToken));
                getNextToken();

            } else {

                throw new SQLParseException(String.format("Invalid member of \"OFFSET\" on %d!",
                        curTokenPos));

            }
        }

        if (isSubQuery && !curToken.lexeme.equals(")")) {

            getNextToken();

        }

    }

    void analyseSubquery(List<Node> children) throws SQLParseException {

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
                           TokenProcessable processToken,
                           TokenComparable subQueryCheck,
                           boolean isColumn) throws SQLParseException {

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
                        || isColumn && t.category == Category.ALL,
                        "Identifier or \"*\" in case of column"));

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

                throw new SQLParseException(String.format("Invalid subquery type on %d!",
                        curTokenPos));

            }

        } else if (curToken.lexeme.equals("CASE")) {

            if (processToken != null) {

                processToken.execute(CaseParser.analyseCase(this,
                        children,
                        null,
                        true));

            }

        } else {

            isFound = false;

        }

        return isFound;
    }

    void analyseAlias(List<Node> children) throws SQLParseException {

        if (curToken.lexeme.equals("AS")) {

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER),
                    "Identifier"));

        } else if (curToken.category == Category.IDENTIFIER) {

            children.add(new Node(NodeType.TERMINAL, new Token("AS" , Category.KEYWORD)));
            children.add(terminal(t -> t.category.equals(Category.IDENTIFIER),
                    "Identifier"));

        } else if (children.getLast().getNodeType() == NodeType.QUERY) {

            throw new SQLParseException(String.format("Subquery is missing elias on %d!",
                    curTokenPos));

        }

    }

    void preProcessBrackets(List<Node> children) {

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
    }

    void postProcessBrackets(List<Node> children,
                             boolean isSubQuery) throws SQLParseException {

        Token bracketToken = stack.peek();

        while (curToken.lexeme.equals(")") && bracketToken.lexeme.equals("(")) {

            stack.pop();
            bracketToken = stack.peek();

            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();

        }

        if (!isSubQuery && curToken.lexeme.equals(")")) {

            throw new SQLParseException(String.format("Invalid brackets on %d!",
                    curTokenPos));

        }

    }

    void checkToken(TokenComparable comparator,
                    String expectedToken) throws SQLParseException {

        if (comparator.execute(curToken)) {

            getNextToken();

        } else {

            throw new SQLParseException(String.format("%s expected instead of %s on %d!",
                    expectedToken,
                    curToken.lexeme,
                    curTokenPos));

        }
    }

    Node terminal(TokenComparable comparator,
                  String expectedToken) throws SQLParseException {

        if (comparator.execute(curToken)) {

            Node terminalNode = new Node(NodeType.TERMINAL, curToken);
            getNextToken();
            return terminalNode;

        } else {

            throw new SQLParseException(String.format("%s expected instead of %s on %d!",
                    expectedToken,
                    curToken.lexeme,
                    curTokenPos));

        }
    }
}
