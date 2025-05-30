package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.List;

import static sql.to.mongodb.translator.service.parser.main.cases.ArithmeticParser.analyseArithmeticExpression;
import static sql.to.mongodb.translator.service.parser.special.cases.BracketsParser.analysePreProcessBrackets;
import static sql.to.mongodb.translator.service.parser.special.cases.OperandParser.analyseOperand;

public class OrderByParser {

    public static Node analyseOrderBy(PushdownAutomaton pA,
                                      List<Node> children,
                                      boolean isSubQuery) throws SQLParseException {

        analysePreProcessBrackets(pA, children);

        if (analyseOperand(pA,
                children,
                PushdownAutomaton::push,
                t -> t.category != Category.PROC_NUMBER,
                false)) {

            analyseArithmeticExpression(pA,
                    children,
                    false,
                    PushdownAutomaton::push,
                    PushdownAutomaton::pop);

            Token token = pA.pop();

            // Проверка на наличие скобок за пределами арифметического выражения
            if (pA.peek().lexeme.equals("(")) {

                throw new SQLParseException(String.format("Invalid brackets in \"ORDER BY\" on %d!",
                        pA.curTokenPos()));

            }

            if (token.category == Category.LITERAL) {

                throw new SQLParseException(String.format("Invalid member of \"ORDER BY\" on %d!",
                        pA.curTokenPos()));

            } else if (token.category == Category.NUMBER && !token.lexeme.equals("NON")) {

                int curNum = Integer.parseInt(token.lexeme);

                if (curNum <= 0 || Integer.parseInt(pA.peek().lexeme) < curNum) {

                    throw new SQLParseException(String.format("Invalid constant number in \"ORDER BY\" on %d!",
                            pA.curTokenPos()));

                }
            }

        } else {

            throw new SQLParseException(String.format("Invalid member of \"ORDER BY\" on %d!",
                    pA.curTokenPos()));

        }

        if (pA.curToken().lexeme.equals("DESC")
                || pA.curToken().lexeme.equals("ASC")) {

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

        } else {

            children.add(new Node(NodeType.TERMINAL, new Token("ASC", Category.KEYWORD)));

        }

        if (pA.curToken().lexeme.equals(",")) {

            pA.getNextToken();
            return analyseOrderBy(pA, children, isSubQuery);

        } else if (pA.isEnd()
                || pA.curToken().category == Category.KEYWORD
                || isSubQuery && pA.curToken().lexeme.equals(")")) {

            return new Node(NodeType.ORDER_BY, children);

        } else {

            throw new SQLParseException(String.format("Invalid link between members of \"ORDER BY\" on %d!",
                    pA.curTokenPos()));

        }
    }
}
