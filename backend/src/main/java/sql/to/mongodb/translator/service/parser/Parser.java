package sql.to.mongodb.translator.service.parser;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.exceptions.SQLScanException;
import sql.to.mongodb.translator.service.scanner.Token;
import sql.to.mongodb.translator.service.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.service.parser.dml.SelectParser.analyseSelect;

@Component
public class Parser {

    PushdownAutomaton pA;

    public Parser(PushdownAutomaton pA) {
        this.pA = pA;
    }

    public Node tryAnalyse(List<Token> tokens, List<String> errors) throws SQLParseException, SQLScanException {

        for (String error : errors) {

            throw new SQLScanException(error);

        }

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
