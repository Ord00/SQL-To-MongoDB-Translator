package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.interfaces.TokenProcessable;
import sql.to.mongodb.translator.service.interfaces.TokenReleasable;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.special.cases.OperandParser.analyseOperand;

public class ArithmeticParser {

    public static boolean analyseArithmeticExpression(PushdownAutomaton pA,
                                                      List<Node> children,
                                                      boolean isColumn,
                                                      TokenProcessable processToken,
                                                      TokenReleasable releaseToken) throws SQLParseException {

        boolean isArithmetic = false;

        List<Node> arithmeticChildren = new ArrayList<>();
        arithmeticChildren.add(children.getLast());

        int closeBracketsLeft = analyseArithmeticRec(pA, arithmeticChildren, isColumn);

        if (arithmeticChildren.size() > 1) {

            isArithmetic = true;

            if (releaseToken != null) {

                releaseToken.execute(pA);

            }

            processToken.execute(pA, new Token("NON", Category.NUMBER));
            children.removeLast();

            while (closeBracketsLeft > 0) {

                arithmeticChildren.addFirst(children.removeLast());
                --closeBracketsLeft;
            }

            children.add(new Node(NodeType.ARITHMETIC_EXP, arithmeticChildren));

        }

        return isArithmetic;
    }

    private static int analyseArithmeticRec(PushdownAutomaton pA,
                                            List<Node> children,
                                            boolean isColumn) throws SQLParseException {

        int closeBracketsLeft = 0;

        if (pA.curToken().category == Category.ARITHMETIC_OPERATOR
                || pA.curToken().category == Category.ALL) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

            closeBracketsLeft -= preProcessBrackets(pA, children);

            if (analyseOperand(pA,
                    children,
                    PushdownAutomaton::push,
                    t -> t.category != Category.PROC_NUMBER && t.category != Category.LITERAL,
                    isColumn)) {

                if (pA.pop().category == Category.LITERAL) {

                    throw new SQLParseException(String.format("Literal is involved in arithmetic operations on %d!",
                            pA.curTokenPos()));

                }

            } else if (pA.curToken().category == Category.AGGREGATE) {

                FunctionsParser.analyseAggregate(pA, children, isColumn);

            } else {

                throw new SQLParseException(String.format("Invalid member of arithmetic operations on %d between %s and %s!",
                        pA.curTokenPos(),
                        pA.curToken().lexeme,
                        pA.token(pA.curTokenPos())));

            }

            closeBracketsLeft += postProcessBrackets(pA, children);

            closeBracketsLeft += analyseArithmeticRec(pA, children, isColumn);
        }

        return closeBracketsLeft;

    }

    private static int preProcessBrackets(PushdownAutomaton pA,
                                          List<Node> children) {

        int openBracketsFound = 0;

        while (pA.curToken().lexeme.equals("(")) {

            openBracketsFound++;
            pA.push(pA.curToken());
            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        }

        if (pA.curToken().lexeme.equals("SELECT")) {

            pA.pop();
            children.removeLast();
            pA.getPrevToken();

        }

        return openBracketsFound;
    }

    private static int postProcessBrackets(PushdownAutomaton pA,
                                           List<Node> children) {

        int closeBracketsFound = 0;

        Token bracketToken = pA.peek();

        while (pA.curToken().lexeme.equals(")") && bracketToken.lexeme.equals("(")) {

            closeBracketsFound++;
            pA.pop();
            bracketToken = pA.peek();

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        }

        return closeBracketsFound;

    }

}
