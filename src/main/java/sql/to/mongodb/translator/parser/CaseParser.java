package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.LambdaComparable;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

public class CaseParser {

    public static void analyseCase(Parser parser,
                                   List<Node> children,
                                   LambdaComparable returnValueCheck,
                                   boolean isColumn) throws Exception {

        children.add(parser.terminal(t -> t.lexeme.equals("CASE"), "CASE"));

        List<Node> caseChildren = new ArrayList<>();

        analyseCasePart(parser,
                caseChildren,
                false,
                returnValueCheck,
                isColumn);

        children.add(new Node(NodeType.CASE, caseChildren));
    }

    private static void analyseCasePart(Parser parser,
                                        List<Node> children,
                                        boolean isCheckStack,
                                        LambdaComparable returnValueCheck,
                                        boolean isColumn) throws Exception {

        if (parser.curToken.lexeme.equals("WHEN")) {

            parser.stack.push(parser.curToken);
            children.add(new Node(NodeType.TERMINAL, parser.curToken));

        } else {

            throw new Exception(String.format("EXISTS expected instead of %s on %d!",
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
                parser.stack.pop();

            }

            case "END" -> {

                children.add(new Node(NodeType.TERMINAL, parser.curToken));
                parser.stack.pop();

            }

            default -> throw new Exception(String.format("Invalid link between \"CASE\" conditions on %d!",
                    parser.curTokenPos));
        }
    }

    private static boolean analyseReturnValue(Parser parser,
                                              List<Node> children,
                                              boolean isCheckStack,
                                              LambdaComparable returnValueCheck,
                                              boolean isColumn) throws Exception {

        boolean newIsCheckStack;

        if (parser.analyseOperand(children,
                t -> parser.stack.push(t),
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            newIsCheckStack = processOperand(parser, isCheckStack, returnValueCheck);

        } else if (parser.curToken.category == Category.AGGREGATE) {

            if (isCheckStack && parser.stack.pop().category != Category.NUMBER) {

                throw new Exception(String.format("Invalid member of \"CASE\" on %d!",
                        parser.curTokenPos));

            }

            if (parser.stack.peek().category == Category.AGGREGATE) {

                throw new Exception(String.format("Attempt to call a nested aggregate function in \"CASE\" on %d!",
                        parser.curTokenPos));
            }

            FunctionsParser.analyseAggregate(parser, children, isColumn);

            parser.stack.push(new Token("1", Category.NUMBER));

            newIsCheckStack = true;

        } else {

            throw new Exception(String.format("Invalid member of \"CASE\" on %d!",
                    parser.curTokenPos));

        }

        return newIsCheckStack;
    }

    private static boolean processOperand(Parser parser,
                                          boolean isCheckStack,
                                          LambdaComparable returnValueCheck) throws Exception {

        boolean newIsCheckStack = isCheckStack;

        Token curAttribute = parser.stack.pop();

        if (returnValueCheck != null
                && !returnValueCheck.execute(curAttribute)) {

            throw new Exception(String.format("Invalid member of \"CASE\" on %d!",
                    parser.curTokenPos));

        }

        if (isCheckStack) {

            Token prevAttribute = parser.stack.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
                    && curAttribute.category != prevAttribute.category) {

                throw new Exception(String.format("Invalid return value of \"CASE\" on %d!",
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
