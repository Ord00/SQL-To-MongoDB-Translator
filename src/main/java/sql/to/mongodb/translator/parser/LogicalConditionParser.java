package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.TokenComparable;

import java.util.ArrayList;
import java.util.List;

public class LogicalConditionParser {

    public static void analyseLogicalCondition(Parser parser,
                                               List<Node> children,
                                               boolean isSubQuery) throws SQLParseException {

        List<Node> logicalCheckChildren = new ArrayList<>();

        parser.getNextToken();

        parser.preProcessBrackets(children);

        if (parser.analyseOperand(logicalCheckChildren,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            parser.analyseArithmeticExpression(logicalCheckChildren,
                    false,
                    t -> parser.stack.push(t),
                    () -> parser.stack.pop());

            analyseOperation(parser, logicalCheckChildren);

        } else if (parser.curToken.lexeme.equals("NOT")) {

            parser.getNextToken();
            logicalCheckChildren.add(parser.terminal(t -> t.lexeme.equals("EXISTS"),
                    "EXISTS"));

            if (!parser.curToken.lexeme.equals("(")) {

                throw new SQLParseException(String.format("Expected \"(\" after EXISTS on %d!",
                        parser.curTokenPos));

            }

            parser.analyseSubquery(logicalCheckChildren);

            parser.checkToken(t -> t.lexeme.equals(")"),
                    ")");

        } else if (parser.curToken.lexeme.equals("EXISTS")) {

            logicalCheckChildren.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

            if (!parser.curToken.lexeme.equals("(")) {

                throw new SQLParseException(String.format("Expected \"(\" after EXISTS on %d!",
                        parser.curTokenPos));

            }

            parser.analyseSubquery(logicalCheckChildren);

            parser.checkToken(t -> t.lexeme.equals(")"), ")");

        } else if (parser.curToken.category == Category.AGGREGATE) {

            FunctionsParser.analyseAggregate(parser, logicalCheckChildren, false);

            parser.analyseArithmeticExpression(logicalCheckChildren,
                    false,
                    t -> parser.stack.push(t),
                    () -> parser.stack.pop());

            analyseOperation(parser, logicalCheckChildren);

        } else {

            throw new SQLParseException(String.format("Invalid left operand of logical expression on %d!",
                    parser.curTokenPos));

        }

        children.add(new Node(NodeType.LOGICAL_CHECK, logicalCheckChildren));

        clearLogicalExpInStack(parser);

        parser.postProcessBrackets(children, isSubQuery);

        if (parser.curToken.category == Category.LOGICAL_COMBINE) {

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            analyseLogicalCondition(parser, children, isSubQuery);

        } else if (parser.curTokenPos == parser.tokens.size()
                || parser.curToken.category == Category.KEYWORD) {

            if ( parser.stack.peek().lexeme.equals("(")) {

                throw new SQLParseException(String.format("Invalid brackets of logical expression on %d!",
                        parser.curTokenPos));

            }

        } else if (!(isSubQuery && parser.curToken.lexeme.equals(")"))) {

            throw new SQLParseException(String.format("Invalid link between logical expressions on %d!",
                    parser.curTokenPos));

        }
    }

    public static void analyseLogicalOperator(Parser parser,
                                              List<Node> children,
                                              TokenComparable comparator,
                                              String expectedToken) throws SQLParseException {

        parser.stack.push(parser.curToken);

        children.add(new Node(NodeType.TERMINAL, parser.curToken));
        parser.getNextToken();

        if (!parser.stack.pop().lexeme.equals("=")
                && parser.curToken.category == Category.LOGICAL_OPERATOR) {

            children.add(parser.terminal(comparator, expectedToken));

        }

        if (!parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            if (parser.curToken.lexeme.equals("ANY")
                    || parser.curToken.lexeme.equals("SOME")
                    || parser.curToken.lexeme.equals("ALL")) {

                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();

                if (!parser.curToken.lexeme.equals("(")) {

                    throw new SQLParseException(String.format("Expected \"(\" after %s on %d",
                            parser.tokens.get(parser.curTokenPos - 2),
                            parser.curTokenPos));

                }

                parser.analyseSubquery(children);

                parser.checkToken(t -> t.lexeme.equals(")"),
                        ")");

                if (parser.stack.peek().category == Category.PROC_NUMBER) {

                    throw new SQLParseException(String.format("Invalid type of subquery on %d!",
                            parser.curTokenPos));

                }

            } else {

                throw new SQLParseException(String.format("Expected %s instead of %s on %d",
                        "[ANY, SOME, ALL]",
                        parser.curToken.lexeme,
                        parser.curTokenPos));

            }
        }
    }

    public static void analyseLike(Parser parser,
                                   List<Node> children) throws SQLParseException {

        Token operandToken = parser.stack.pop();

        if (operandToken.category != Category.IDENTIFIER
                && operandToken.category != Category.LITERAL) {

            throw new SQLParseException(String.format("Invalid left operand of \"LIKE\" on %d!",
                    parser.curTokenPos));

        }

        children.add(new Node(NodeType.TERMINAL, parser.curToken));
        parser.getNextToken();

        if (!parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)
                || parser.stack.pop().category == Category.NUMBER) {

            throw new SQLParseException(String.format("Invalid right operand of \"LIKE\" on on %d!",
                    parser.curTokenPos));

        }
    }

    public static Node analyseIn(Parser parser,
                                 List<Node> children,
                                 boolean isCheckStack) throws SQLParseException {

        boolean newIsCheckStack = isCheckStack;

        if (!parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new SQLParseException(String.format("Invalid member of \"IN\" on %d!",
                    parser.curTokenPos));

        }

        Token curAttribute = parser.stack.pop();

        if (isCheckStack) {

            Token prevAttribute = parser.stack.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
            && curAttribute.category != prevAttribute.category) {

                throw new SQLParseException(String.format("Invalid attribute of \"IN\" on %d!",
                        parser.curTokenPos));

            }

        } else if (curAttribute.category == Category.NUMBER
                || curAttribute.category == Category.LITERAL) {

            parser.stack.push(curAttribute);
            newIsCheckStack = true;
            
        }

        return switch (parser.curToken.lexeme) {

            case "," -> {

                parser.getNextToken();
                yield analyseIn(parser, children, newIsCheckStack);

            }

            case ")" -> {

                if (isCheckStack) {
                    parser.stack.pop();
                }
                yield new Node(NodeType.ATTRIBUTES, children);

            }

            default -> throw new SQLParseException(String.format("Invalid link between members of \"IN\" on %d",
                    parser.curTokenPos));
        };
    }

    public static boolean analyseInOfSubquery(Parser parser,
                                              List<Node> children,
                                              TokenComparable subQueryCheck) throws SQLParseException {

        boolean isFound = true;

        if (parser.curToken.lexeme.equals("SELECT")) {

            parser.getPrevToken();
            parser.analyseSubquery(children);

            if (!subQueryCheck.execute(parser.stack.peek())) {

                throw new SQLParseException(String.format("Wrong first of column_names on %s",
                        parser.curTokenPos));

            } else if (parser.curToken.lexeme.equals(")")) {

                children.add(new Node(NodeType.ATTRIBUTES, List.of(children.removeLast())));
                parser.getNextToken();
            }

        } else {

            isFound = false;

        }

        return !isFound;

    }

    public static void analyseBetween(Parser parser,
                                      List<Node> children) throws SQLParseException {

        if (!parser.analyseOperand(children,
                null,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new SQLParseException(String.format("Invalid left border of \"BETWEEN\" range on %d",
                    parser.curTokenPos));

        }


        children.add(parser.terminal(t -> t.lexeme.equals("AND"), "AND"));
        parser.getNextToken();

        if (!parser.analyseOperand(children,
                null,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new SQLParseException(String.format("Invalid right border of \"BETWEEN\" range on %d",
                    parser.curTokenPos));

        }

    }

    public static void analyseOperation(Parser parser,
                                        List<Node> children) throws SQLParseException {

        switch (parser.curToken.lexeme) {

            case "=" -> analyseLogicalOperator(parser,
                    children,
                    _ -> true,
                    "");

            case "<" -> analyseLogicalOperator(parser,
                    children,
                    t -> t.lexeme.equals(">") || t.lexeme.equals("="),
                    "[>, =]");

            case ">" -> analyseLogicalOperator(parser,
                    children,
                    t -> t.lexeme.equals("="),
                    "=");

            case "IS" -> {

                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();

                if (parser.curToken.lexeme.equals("NOT")) {

                    children.add(new Node(NodeType.TERMINAL, parser.curToken));
                    parser.getNextToken();

                }

                children.add(parser.terminal(t -> t.category == Category.NULL, "NULL"));

            }
            case "NOT" -> {
                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();

                switch (parser.curToken.lexeme) {
                    case "LIKE" -> {
                        children.add(new Node(NodeType.TERMINAL, parser.curToken));
                        parser.getNextToken();
                        analyseLike(parser, children);
                    }
                    case "IN" -> {
                        children.add(new Node(NodeType.TERMINAL, parser.curToken));
                        parser.getNextToken();
                        parser.checkToken(t -> t.lexeme.equals("("), "(");

                        if (analyseInOfSubquery(parser, children, t -> t.category != Category.PROC_NUMBER)) {

                            List<Node> inChildren = new ArrayList<>();
                            analyseIn(parser, inChildren, false);

                        }
                    }
                    case "BETWEEN" -> analyseBetween(parser, children);
                    default -> throw new SQLParseException(String.format("%s expected instead of %s on %d!",
                            "[LIKE, IN, BETWEEN]",
                            parser.curToken.lexeme,
                            parser.curTokenPos));
                }
            }
            case "LIKE" -> {
                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();
                analyseLike(parser, children);
            }
            case "IN" -> {
                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();
                parser.checkToken(t -> t.lexeme.equals("("), "(");

                if (analyseInOfSubquery(parser, children, t -> t.category != Category.PROC_NUMBER)) {

                    List<Node> inChildren = new ArrayList<>();
                    analyseIn(parser, inChildren, false);

                }
            }
            case "BETWEEN" -> analyseBetween(parser, children);
            default -> throw new SQLParseException(String.format("Invalid logical operation on %d!",
                    parser.curTokenPos));
        }
    }

    public static void clearLogicalExpInStack(Parser parser) {

        Token curStackToken = parser.stack.peek();

        while (curStackToken.category != Category.KEYWORD
                && !curStackToken.lexeme.equals("(")) {

            parser.stack.pop();
            curStackToken = parser.stack.peek();

        }

    }
}
