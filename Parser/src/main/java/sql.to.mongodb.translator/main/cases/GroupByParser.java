package sql.to.mongodb.translator.main.cases;

import sql.to.mongodb.translator.PushdownAutomaton;
import sql.to.mongodb.translator.SQLParseException;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Category;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

import static sql.to.mongodb.translator.main.cases.ArithmeticParser.analyseArithmeticExpression;
import static sql.to.mongodb.translator.special.cases.BracketsParser.analysePreProcessBrackets;
import static sql.to.mongodb.translator.special.cases.OperandParser.analyseOperand;

public class GroupByParser {

    public static Node analyseGroupBy(PushdownAutomaton pA,
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

            if (token.category == Category.LITERAL
                    || token.category == Category.NUMBER && !token.lexeme.equals("NON")) {

                throw new SQLParseException(String.format("Invalid member of \"GROUP BY\" on %d!",
                        pA.curTokenPos()));

            }

        } else {

            throw new SQLParseException(String.format("Invalid member of \"GROUP BY\" on %d!",
                    pA.curTokenPos()));

        }

        // Проверка на наличие скобок за пределами арифметического выражения
        if (pA.peek().lexeme.equals("(")) {

            throw new SQLParseException(String.format("Invalid brackets in \"GROUP BY\" on %d!",
                    pA.curTokenPos()));

        }

        if (pA.curToken().lexeme.equals(",")) {

            pA.getNextToken();
            return analyseGroupBy(pA, children, isSubQuery);

        } else if (pA.isEnd()
                || pA.curToken().category == Category.KEYWORD
                || isSubQuery && pA.curToken().lexeme.equals(")")) {

            return new Node(NodeType.GROUP_BY, children);

        } else {

            throw new SQLParseException(String.format("Invalid link between members of \"GROUP BY\" on %d!",
                    pA.curTokenPos()));

        }
    }
}
