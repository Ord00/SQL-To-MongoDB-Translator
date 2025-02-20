package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.interfaces.TokenComparable;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

public class CaseParser {

    public static Token analyseCase(Parser parser,
                                   List<Node> children,
                                   TokenComparable returnValueCheck,
                                   boolean isColumn) throws SQLParseException {

        children.add(parser.terminal(t -> t.lexeme.equals("CASE"), "CASE"));

        List<Node> caseChildren = new ArrayList<>();

        Token returnValue = analyseCasePart(parser,
                caseChildren,
                false,
                returnValueCheck,
                isColumn);

        children.add(new Node(NodeType.CASE, caseChildren));

        return returnValue;
    }

    private static Token analyseCasePart(Parser parser,
                                        List<Node> children,
                                        boolean isCheckStack,
                                        TokenComparable returnValueCheck,
                                        boolean isColumn) throws SQLParseException {

        Token returnValue = new Token("UNDEFINED", Category.UNDEFINED);

        if (parser.curToken.lexeme.equals("WHEN")) {

            parser.stack.push(parser.curToken);
            children.add(new Node(NodeType.TERMINAL, parser.curToken));

        } else {

            throw new SQLParseException(String.format("EXISTS expected instead of %s on %d!",
                    parser.curToken.lexeme,
                    parser.curTokenPos));
        }

        LogicalConditionParser.analyseLogicalCondition(parser, children, false);

        parser.stack.pop();

        children.add(parser.terminal(t -> t.lexeme.equals("THEN"), "THEN"));

        boolean newIsCheckStack = analyseReturnValue(parser,
                children,
                isCheckStack,
                returnValueCheck,
                isColumn);

        switch (parser.curToken.lexeme) {

            case "WHEN" -> analyseCasePart(parser,
                    children,
                    newIsCheckStack,
                    returnValueCheck,
                    isColumn);

            case "ELSE" -> {

                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.getNextToken();

                analyseReturnValue(parser,
                        children,
                        newIsCheckStack,
                        returnValueCheck,
                        isColumn);

                children.add(parser.terminal(t -> t.lexeme.equals("END"), "END"));

                if (isCheckStack) {

                    returnValue =  parser.stack.pop();
                }

            }

            case "END" -> {

                children.add(new Node(NodeType.TERMINAL, parser.curToken));

                if (isCheckStack) {

                    returnValue = parser.stack.pop();
                }

            }

            default -> throw new SQLParseException(String.format("Invalid link between \"CASE\" conditions on %d!",
                    parser.curTokenPos));
        }

        return returnValue;
    }

    private static boolean analyseReturnValue(Parser parser,
                                              List<Node> children,
                                              boolean isCheckStack,
                                              TokenComparable returnValueCheck,
                                              boolean isColumn) throws SQLParseException {

        boolean newIsCheckStack;

        if (parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            newIsCheckStack = processOperand(parser, isCheckStack, returnValueCheck);

        } else if (parser.curToken.category == Category.AGGREGATE) {

            if (isCheckStack && parser.stack.pop().category != Category.NUMBER) {

                throw new SQLParseException(String.format("Invalid member of \"CASE\" on %d!",
                        parser.curTokenPos));

            }

            if (parser.stack.peek().category == Category.AGGREGATE) {

                throw new SQLParseException(String.format("Attempt to call a nested aggregate function in \"CASE\" on %d!",
                        parser.curTokenPos));
            }

            FunctionsParser.analyseAggregate(parser, children, isColumn);

            parser.stack.push(new Token("1", Category.NUMBER));

            newIsCheckStack = true;

        } else {

            throw new SQLParseException(String.format("Invalid member of \"CASE\" on %d!",
                    parser.curTokenPos));

        }

        return newIsCheckStack;
    }

    private static boolean processOperand(Parser parser,
                                          boolean isCheckStack,
                                          TokenComparable returnValueCheck) throws SQLParseException {

        boolean newIsCheckStack = isCheckStack;

        Token curAttribute = parser.stack.pop();

        if (returnValueCheck != null
                && !returnValueCheck.execute(curAttribute)) {

            throw new SQLParseException(String.format("Invalid member of \"CASE\" on %d!",
                    parser.curTokenPos));

        }

        if (isCheckStack) {

            Token prevAttribute = parser.stack.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
                    && curAttribute.category != prevAttribute.category) {

                throw new SQLParseException(String.format("Invalid return value of \"CASE\" on %d!",
                        parser.curTokenPos));

            }

        } else if (curAttribute.category == Category.NUMBER
                || curAttribute.category == Category.LITERAL) {

            parser.stack.push(curAttribute);
            newIsCheckStack = true;

        }

        return newIsCheckStack;
    }
}
