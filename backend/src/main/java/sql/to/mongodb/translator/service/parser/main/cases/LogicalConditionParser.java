package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.interfaces.TokenComparable;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.dml.SelectParser.analyseSubquery;
import static sql.to.mongodb.translator.service.parser.main.cases.ArithmeticParser.analyseArithmeticExpression;
import static sql.to.mongodb.translator.service.parser.special.cases.BracketsParser.analysePostProcessBrackets;
import static sql.to.mongodb.translator.service.parser.special.cases.BracketsParser.analysePreProcessBrackets;
import static sql.to.mongodb.translator.service.parser.special.cases.OperandParser.analyseOperand;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.checkToken;
import static sql.to.mongodb.translator.service.parser.special.cases.TokenHandler.terminal;

public class LogicalConditionParser {

    public static void analyseLogicalCondition(PushdownAutomaton pA,
                                               List<Node> children,
                                               boolean isSubQuery) throws SQLParseException {

        List<Node> logicalCheckChildren = new ArrayList<>();

        pA.getNextToken();

        analysePreProcessBrackets(pA, children);

        if (analyseOperand(pA,
                logicalCheckChildren,
                PushdownAutomaton::push,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            analyseArithmeticExpression(pA,
                    logicalCheckChildren,
                    false,
                    PushdownAutomaton::push,
                    PushdownAutomaton::pop);

            analyseOperation(pA, logicalCheckChildren);

        } else if (pA.curToken().lexeme.equals("NOT")) {

            pA.getNextToken();
            logicalCheckChildren.add(terminal(pA,
                    t -> t.lexeme.equals("EXISTS"),
                    "EXISTS"));

            if (!pA.curToken().lexeme.equals("(")) {

                throw new SQLParseException(String.format("Expected \"(\" after EXISTS on %d!",
                        pA.curTokenPos()));

            }

            analyseSubquery(pA, logicalCheckChildren);

            checkToken(pA,
                    t -> t.lexeme.equals(")"),
                    ")");

        } else if (pA.curToken().lexeme.equals("EXISTS")) {

            logicalCheckChildren.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

            if (!pA.curToken().lexeme.equals("(")) {

                throw new SQLParseException(String.format("Expected \"(\" after EXISTS on %d!",
                        pA.curTokenPos()));

            }

            analyseSubquery(pA, logicalCheckChildren);

            checkToken(pA,
                    t -> t.lexeme.equals(")"), ")");

        } else if (pA.curToken().category == Category.AGGREGATE) {

            FunctionsParser.analyseAggregate(pA, logicalCheckChildren, false);

            analyseArithmeticExpression(pA,
                    logicalCheckChildren,
                    false,
                    PushdownAutomaton::push,
                    PushdownAutomaton::pop);

            analyseOperation(pA, logicalCheckChildren);

        } else {

            throw new SQLParseException(String.format("Invalid left operand of logical expression on %d!",
                    pA.curTokenPos()));

        }

        children.add(new Node(NodeType.LOGICAL_CHECK, logicalCheckChildren));

        clearLogicalExpInStack(pA);

        analysePostProcessBrackets(pA, children, isSubQuery);

        if (pA.curToken().category == Category.LOGICAL_COMBINE) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            analyseLogicalCondition(pA, children, isSubQuery);

        } else if (pA.isEnd()
                || pA.curToken().category == Category.KEYWORD) {

            if (pA.peek().lexeme.equals("(")) {

                throw new SQLParseException(String.format("Invalid brackets of logical expression on %d!",
                        pA.curTokenPos()));

            }

        } else if (!(isSubQuery && pA.curToken().lexeme.equals(")"))) {

            throw new SQLParseException(String.format("Invalid link between logical expressions on %d!",
                    pA.curTokenPos()));

        }
    }

    public static void analyseLogicalOperator(PushdownAutomaton pA,
                                              List<Node> children,
                                              TokenComparable comparator,
                                              String expectedToken) throws SQLParseException {

        pA.push(pA.curToken());

        children.add(new Node(NodeType.TERMINAL, pA.curToken()));
        pA.getNextToken();

        if (!pA.pop().lexeme.equals("=")
                && pA.curToken().category == Category.LOGICAL_OPERATOR) {

            // children.add(parser.terminal(comparator, expectedToken));

            children.add(new Node(NodeType.TERMINAL,
                    new Token(children.removeLast().getToken().lexeme
                            + terminal(pA, comparator, expectedToken).getToken().lexeme,
                            Category.LOGICAL_OPERATOR)));

        }

        if (!analyseOperand(pA,
                children,
                PushdownAutomaton::push,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            if (pA.curToken().lexeme.equals("ANY")
                    || pA.curToken().lexeme.equals("SOME")
                    || pA.curToken().lexeme.equals("ALL")) {

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

                if (!pA.curToken().lexeme.equals("(")) {

                    throw new SQLParseException(String.format("Expected \"(\" after %s on %d",
                            pA.token(pA.curTokenPos() - 2),
                            pA.curTokenPos()));

                }

                analyseSubquery(pA, children);

               checkToken(pA,
                       t -> t.lexeme.equals(")"),
                        ")");

                if (pA.peek().category == Category.PROC_NUMBER) {

                    throw new SQLParseException(String.format("Invalid type of subquery on %d!",
                            pA.curTokenPos()));

                }

            } else {

                throw new SQLParseException(String.format("Expected %s instead of %s on %d",
                        "[ANY, SOME, ALL]",
                        pA.curToken().lexeme,
                        pA.curTokenPos()));

            }
        }
    }

    public static void analyseLike(PushdownAutomaton pA,
                                   List<Node> children) throws SQLParseException {

        Token operandToken = pA.pop();

        if (operandToken.category != Category.IDENTIFIER
                && operandToken.category != Category.LITERAL) {

            throw new SQLParseException(String.format("Invalid left operand of \"LIKE\" on %d!",
                    pA.curTokenPos()));

        }

        children.add(new Node(NodeType.TERMINAL, pA.curToken()));
        pA.getNextToken();

        if (!analyseOperand(pA,
                children,
                PushdownAutomaton::push,
                t -> t.category != Category.PROC_NUMBER,
                false)
                || pA.pop().category == Category.NUMBER) {

            throw new SQLParseException(String.format("Invalid right operand of \"LIKE\" on on %d!",
                    pA.curTokenPos()));

        }
    }

    public static Node analyseIn(PushdownAutomaton pA,
                                 List<Node> children,
                                 boolean isCheckStack) throws SQLParseException {

        boolean newIsCheckStack = isCheckStack;

        if (!analyseOperand(pA,
                children,
                PushdownAutomaton::push,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new SQLParseException(String.format("Invalid member of \"IN\" on %d!",
                    pA.curTokenPos()));

        }

        Token curAttribute = pA.pop();

        if (isCheckStack) {

            Token prevAttribute = pA.peek();

            if ((curAttribute.category == Category.NUMBER || curAttribute.category == Category.LITERAL)
            && curAttribute.category != prevAttribute.category) {

                throw new SQLParseException(String.format("Invalid attribute of \"IN\" on %d!",
                        pA.curTokenPos()));

            }

        } else if (curAttribute.category == Category.NUMBER
                || curAttribute.category == Category.LITERAL) {

            pA.push(curAttribute);
            newIsCheckStack = true;
            
        }

        return switch (pA.curToken().lexeme) {

            case "," -> {

                pA.getNextToken();
                yield analyseIn(pA, children, newIsCheckStack);

            }

            case ")" -> {

                if (isCheckStack) {
                    pA.pop();
                }
                yield new Node(NodeType.ATTRIBUTES, children);

            }

            default -> throw new SQLParseException(String.format("Invalid link between members of \"IN\" on %d",
                    pA.curTokenPos()));
        };
    }

    public static boolean analyseInOfSubquery(PushdownAutomaton pA,
                                              List<Node> children,
                                              TokenComparable subQueryCheck) throws SQLParseException {

        boolean isFound = true;

        if (pA.curToken().lexeme.equals("SELECT")) {

            pA.getPrevToken();
            analyseSubquery(pA, children);

            if (!subQueryCheck.execute(pA.peek())) {

                throw new SQLParseException(String.format("Wrong first of column_names on %s",
                        pA.curTokenPos()));

            } else if (pA.curToken().lexeme.equals(")")) {

                children.add(new Node(NodeType.ATTRIBUTES, List.of(children.removeLast())));
                pA.getNextToken();
            }

        } else {

            isFound = false;

        }

        return !isFound;

    }

    public static void analyseBetween(PushdownAutomaton pA,
                                      List<Node> children) throws SQLParseException {

        if (!analyseOperand(pA,
                children,
                null,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new SQLParseException(String.format("Invalid left border of \"BETWEEN\" range on %d",
                    pA.curTokenPos()));

        }


        children.add(terminal(pA,
                t -> t.lexeme.equals("AND"), "AND"));
        pA.getNextToken();

        if (!analyseOperand(pA,
                children,
                null,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            throw new SQLParseException(String.format("Invalid right border of \"BETWEEN\" range on %d",
                    pA.curTokenPos()));

        }

    }

    public static void analyseOperation(PushdownAutomaton pA,
                                        List<Node> children) throws SQLParseException {

        switch (pA.curToken().lexeme) {

            case "=" -> analyseLogicalOperator(pA,
                    children,
                    _ -> true,
                    "");

            case "<" -> analyseLogicalOperator(pA,
                    children,
                    t -> t.lexeme.equals(">") || t.lexeme.equals("="),
                    "[>, =]");

            case ">" -> analyseLogicalOperator(pA,
                    children,
                    t -> t.lexeme.equals("="),
                    "=");

            case "IS" -> {

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

                if (pA.curToken().lexeme.equals("NOT")) {

                    children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                    pA.getNextToken();

                }

                children.add(terminal(pA,
                        t -> t.category == Category.NULL,
                        "NULL"));

            }

            case "NOT" -> {

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();

                switch (pA.curToken().lexeme) {

                    case "LIKE" -> {

                        children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                        pA.getNextToken();
                        analyseLike(pA, children);

                    }

                    case "IN" -> analyseInPart(pA, children);

                    case "BETWEEN" -> analyseBetween(pA, children);

                    default -> throw new SQLParseException(String.format("%s expected instead of %s on %d!",
                            "[LIKE, IN, BETWEEN]",
                            pA.curToken().lexeme,
                            pA.curTokenPos()));

                }

            }

            case "LIKE" -> {

                children.add(new Node(NodeType.TERMINAL, pA.curToken()));
                pA.getNextToken();
                analyseLike(pA, children);

            }

            case "IN" -> analyseInPart(pA, children);

            case "BETWEEN" -> analyseBetween(pA, children);

            default -> throw new SQLParseException(String.format("Invalid logical operation on %d!",
                    pA.curTokenPos()));

        }
    }

    private static void analyseInPart(PushdownAutomaton pA,
                                      List<Node> children) throws SQLParseException {

        children.add(new Node(NodeType.TERMINAL, pA.curToken()));
        pA.getNextToken();
        checkToken(pA,
                t -> t.lexeme.equals("("), "(");

        if (analyseInOfSubquery(pA, children,
                t -> t.category != Category.PROC_NUMBER)) {

            List<Node> inChildren = new ArrayList<>();
            analyseIn(pA, inChildren, false);

        }

    }

    private static void clearLogicalExpInStack(PushdownAutomaton pA) {

        while (pA.peek().category != Category.KEYWORD
                && !pA.peek().lexeme.equals("(")) {

            pA.pop();

        }

    }
}
