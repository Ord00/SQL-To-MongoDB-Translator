package sql.to.mongodb.translator.service.parser.main.cases;

import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.PushdownAutomaton;
import sql.to.mongodb.translator.service.scanner.Token;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;

import java.util.List;

import static sql.to.mongodb.translator.service.parser.special.cases.AliasParser.analyseAlias;
import static sql.to.mongodb.translator.service.parser.special.cases.BracketsParser.analysePreProcessBrackets;
import static sql.to.mongodb.translator.service.parser.special.cases.OperandParser.analyseOperand;

public class ColumnNamesParser {

    public static Node analyseColumnNames(PushdownAutomaton pA,
                                          List<Node> children) throws SQLParseException {

        analysePreProcessBrackets(pA, children);

        final Token[] identifierToken = new Token[1];

        if (pA.curToken().category == Category.ALL) {

            pA.push(new Token("2", Category.PROC_NUMBER));

            children.add(new Node(NodeType.TERMINAL, pA.curToken()));
            pA.getNextToken();

            if (!pA.curToken().lexeme.equals("FROM")) {

                throw new SQLParseException(String.format("Expected \"FROM\" instead of %s on %d!",
                        pA.curToken(),
                        pA.curTokenPos()));

            }

            return new Node(NodeType.COLUMN_NAMES, children);

        } else if (pA.curToken().category == Category.AGGREGATE) {

            processColumnThroughStack(pA, pA.curToken());

            FunctionsParser.analyseAggregate(pA, children, true);

            ArithmeticParser.analyseArithmeticExpression(pA,
                    children,
                    true,
                    ColumnNamesParser::processColumnThroughStack,
                    ColumnNamesParser::releaseColumnThroughStack);

        } else if (analyseOperand(pA,
                children,
                (_, t) -> identifierToken[0] = t,
                t -> t.category != Category.PROC_NUMBER,
                true)) {

            if (!ArithmeticParser.analyseArithmeticExpression(pA,
                    children,
                    true,
                    ColumnNamesParser::processColumnThroughStack,
                    null)) {

                processColumnThroughStack(pA, identifierToken[0]);

            }

        } else {

            throw new SQLParseException(String.format("Incorrect attribute on %d!",
                    pA.curTokenPos()));

        }

        // Проверка на наличие скобок за пределами арифметического выражения
        if (pA.peek().lexeme.equals("(")) {

            throw new SQLParseException(String.format("Invalid brackets in \"FROM\" on %d!",
                    pA.curTokenPos()));

        }

        analyseAlias(pA, children);

        return switch (pA.curToken().lexeme) {

            case "," -> {

                pA.getNextToken();
                yield analyseColumnNames(pA, children);

            }
            case "FROM" -> new Node(NodeType.COLUMN_NAMES, children);
            default -> throw new SQLParseException(String.format("Invalid link between attributes on %d!",
                    pA.curTokenPos()));

        };
    }

    private static void processColumnThroughStack(PushdownAutomaton pA,
                                                  Token token) {

        Token prevToken = pA.pop();

        if (prevToken.category == Category.PROC_NUMBER) {

            if (prevToken.lexeme.equals("0")) {

                pA.push(token);

            } else {

                int procNum = Integer.parseInt(prevToken.lexeme);
                procNum++;
                pA.push(new Token(Integer.toString(procNum), Category.PROC_NUMBER));

            }

        } else {

            pA.push(new Token("2", Category.PROC_NUMBER));

        }
    }

    private static void releaseColumnThroughStack(PushdownAutomaton pA) {

        Token prevToken = pA.pop();

        if (prevToken.category == Category.PROC_NUMBER) {

            int procNum = Integer.parseInt(prevToken.lexeme);
            --procNum;
            pA.push(new Token(Integer.toString(procNum), Category.PROC_NUMBER));

        } else {

            pA.push(new Token("0", Category.PROC_NUMBER));

        }
    }
}
