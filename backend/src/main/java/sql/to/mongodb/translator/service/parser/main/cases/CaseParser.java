package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.interfaces.TokenComparable;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.special.cases.OperandParser.analyseOperand;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.terminal;

public class CaseParser {

    public static Token analyseCase(PushdownAutomaton pA,
                                    List<Node> children,
                                    TokenComparable returnValueCheck,
                                    boolean isColumn) throws SQLParseException {

        children.add(terminal(pA,
                t -> t.lexeme.equals("CASE"), "CASE"));

        List<Node> caseChildren = new ArrayList<>();

        Token returnValue = analyseCasePart(pA,
                caseChildren,
                false,
                returnValueCheck,
                isColumn);

        children.add(new Node(NodeType.CASE, caseChildren));

        return returnValue;
    }

    private static Token analyseCasePart(PushdownAutomaton pA,
                                        List<Node> children,
                                        boolean isCheckStack,
                                        TokenComparable returnValueCheck,
                                        boolean isColumn) throws SQLParseException {

        Token returnValue = new Token("UNDEFINED", Category.UNDEFINED);

        if (pA.curToken().lexeme.equals("WHEN")) {

            pA.push(pA.curToken());
            children.add(new Node(NodeType.TERMINAL, pA.curToken()));

        } else {

            throw new SQLParseException(String.format("EXISTS expected instead of %s on %d!",
                    pA.curToken().lexeme,
                    pA.curTokenPos()));
        }

        LogicalConditionParser.analyseLogicalCondition(pA, children, false);

        pA.pop();

        children.add(terminal(pA,
                t -> t.lexeme.equals("THEN"), "THEN"));

        boolean newIsCheckStack = analyseReturnValue(pA,
                children,
                isCheckStack,
                returnValueCheck,
                isColumn);

        switch (pA.curToken().lexeme) {

            case "WHEN" -> analyseCasePart(pA,
                    children,
                    newIsCheckStack,
                    returnValueCheck,
                    isColumn);

            case "ELSE" -> {

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

                analyseReturnValue(pA,
                        children,
                        newIsCheckStack,
                        returnValueCheck,
                        isColumn);

                children.add(terminal(pA,
                        t -> t.lexeme.equals("END"), "END"));

                if (isCheckStack) {

                    returnValue = pA.pop();
                }

            }

            case "END" -> {

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));

                if (isCheckStack) {

                    returnValue = pA.pop();
                }

            }

            default -> throw new SQLParseException(String.format("Invalid link between \"CASE\" conditions on %d!",
                    pA.curTokenPos()));
        }

        return returnValue;
    }

    private static boolean analyseReturnValue(PushdownAutomaton pA,
                                              List<Node> children,
                                              boolean isCheckStack,
                                              TokenComparable returnValueCheck,
                                              boolean isColumn) throws SQLParseException {

        boolean newIsCheckStack;

        if (analyseOperand(pA,
                children,
                PushdownAutomaton::push,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            newIsCheckStack = processOperand(pA, isCheckStack, returnValueCheck);

        } else if (pA.curToken().category == Category.AGGREGATE) {

            if (isCheckStack && pA.pop().category != Category.NUMBER) {

                throw new SQLParseException(String.format("Invalid member of \"CASE\" on %d!",
                        pA.curTokenPos()));

            }

            if (pA.peek().category == Category.AGGREGATE) {

                throw new SQLParseException(String.format("Attempt to call a nested aggregate function in \"CASE\" on %d!",
                        pA.curTokenPos()));
            }

            FunctionsParser.analyseAggregate(pA, children, isColumn);

            pA.push(new Token("1", Category.NUMBER));

            newIsCheckStack = true;

        } else {

            throw new SQLParseException(String.format("Invalid member of \"CASE\" on %d!",
                    pA.curTokenPos()));

        }

        return newIsCheckStack;
    }

    private static boolean processOperand(PushdownAutomaton pA,
                                          boolean isCheckStack,
                                          TokenComparable returnValueCheck) throws SQLParseException {

        boolean newIsCheckStack = isCheckStack;

        Token curAttribute = pA.pop();

        if (returnValueCheck != null
                && !returnValueCheck.execute(curAttribute)) {

            throw new SQLParseException(String.format("Invalid member of \"CASE\" on %d!",
                    pA.curTokenPos()));

        }

        if (isCheckStack) {

            Token prevAttribute = pA.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
                    && curAttribute.category != prevAttribute.category) {

                throw new SQLParseException(String.format("Invalid return value of \"CASE\" on %d!",
                        pA.curTokenPos()));

            }

        } else if (curAttribute.category == Category.NUMBER
                || curAttribute.category == Category.LITERAL) {

            pA.push(curAttribute);
            newIsCheckStack = true;

        }

        return newIsCheckStack;
    }
}
