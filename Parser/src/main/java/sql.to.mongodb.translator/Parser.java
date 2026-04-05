package sql.to.mongodb.translator;

import org.springframework.stereotype.Component;
import exceptions.SQLParseException;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.dml.SelectParser.analyseSelect;

@Component
public class Parser {

    PushdownAutomaton pA;

    public Parser(PushdownAutomaton pA) {
        this.pA = pA;
    }

    public Node tryAnalyse(List<Token> tokens) throws SQLParseException {

        pA.init(tokens);
        List<Node> children = new ArrayList<>();

        pA.getNextToken();

        switch (pA.curToken().lexeme) {

            case "SELECT" -> analyseSelect(pA,
                    children,
                    false);

            default -> throw new SQLParseException("Invalid query keyword!");

        }

        if (!pA.isEnd()) {

            throw new SQLParseException(String.format("Expected end of query on %d!",
                    pA.curTokenPos()));

        }

        return new Node(NodeType.QUERY, children);
    }

}
