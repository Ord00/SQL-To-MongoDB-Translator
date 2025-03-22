package sql.to.mongodb.translator.service.parser;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.interfaces.TokenProcessable;
import sql.to.mongodb.translator.service.interfaces.TokenReleasable;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

public class ArithmeticParser {

    public static boolean analyseArithmeticExpression(Parser parser,
                                                      List<Node> children,
                                                      boolean isColumn,
                                                      TokenProcessable processToken,
                                                      TokenReleasable releaseToken) throws SQLParseException {

        boolean isArithmetic = false;

        List<Node> arithmeticChildren = new ArrayList<>();
        arithmeticChildren.add(children.getLast());

        int closeBracketsLeft = analyseArithmeticRec(parser, arithmeticChildren, isColumn);

        if (arithmeticChildren.size() > 1) {

            isArithmetic = true;

            if (releaseToken != null) {

                releaseToken.execute();

            }

            processToken.execute(new Token("NON", Category.NUMBER));
            children.removeLast();

            while (closeBracketsLeft > 0) {

                arithmeticChildren.addFirst(children.removeLast());
                --closeBracketsLeft;
            }

            children.add(new Node(NodeType.ARITHMETIC_EXP, arithmeticChildren));

        }

        return isArithmetic;
    }

    private static int analyseArithmeticRec(Parser parser,
                                            List<Node> children,
                                            boolean isColumn) throws SQLParseException {

        int closeBracketsLeft = 0;

        if (parser.curToken.category == Category.ARITHMETIC_OPERATOR
                || parser.curToken.category == Category.ALL) {

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

            closeBracketsLeft -= preProcessBrackets(parser, children);

            if (parser.analyseOperand(children,
                    t -> parser.stack.push(t),
                    t -> t.category != Category.PROC_NUMBER && t.category != Category.LITERAL,
                    isColumn)) {

                if (parser.stack.pop().category == Category.LITERAL) {

                    throw new SQLParseException(String.format("Literal is involved in arithmetic operations on %d!",
                            parser.curTokenPos));

                }

            } else if (parser.curToken.category == Category.AGGREGATE) {

                FunctionsParser.analyseAggregate(parser, children, isColumn);

            } else {

                throw new SQLParseException(String.format("Invalid member of arithmetic operations on %d between %s and %s!",
                        parser.curTokenPos,
                        parser.curToken.lexeme,
                        parser.tokens.get(parser.curTokenPos)));

            }

            closeBracketsLeft += postProcessBrackets(parser, children);

            closeBracketsLeft += analyseArithmeticRec(parser, children, isColumn);
        }

        return closeBracketsLeft;

    }

    private static int preProcessBrackets(Parser parser,
                                          List<Node> children) {

        int openBracketsFound = 0;

        while (parser.curToken.lexeme.equals("(")) {

            openBracketsFound++;
            parser.stack.push(parser.curToken);
            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

        }

        if (parser.curToken.lexeme.equals("SELECT")) {

            parser.stack.pop();
            children.removeLast();
            parser.getPrevToken();

        }

        return openBracketsFound;
    }

    private static int postProcessBrackets(Parser parser,
                                           List<Node> children) {

        int closeBracketsFound = 0;

        Token bracketToken = parser.stack.peek();

        while (parser.curToken.lexeme.equals(")") && bracketToken.lexeme.equals("(")) {

            closeBracketsFound++;
            parser.stack.pop();
            bracketToken = parser.stack.peek();

            children.add(new Node(NodeType.TERMINAL, parser.curToken));
            parser.getNextToken();

        }

        return closeBracketsFound;

    }

}
