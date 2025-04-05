package sql.to.mongodb.translator.service.parser;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.exceptions.SQLScanException;
import sql.to.mongodb.translator.service.scanner.Token;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.interfaces.TokenProcessable;
import sql.to.mongodb.translator.service.interfaces.TokenComparable;

import java.util.ArrayList;
import java.util.List;

@Component
public class Parser {

    PushdownAutomaton pA;
    List<String> errors;

    public Parser(List<Token> tokens, List<String> errors) {

        pA = new PushdownAutomaton(tokens);
        this.errors =  errors;

    }

    public Node tryAnalyse() throws SQLParseException, SQLScanException {

        for (String error : errors) {

            throw new SQLScanException(error);
        }

        List<Node> children = new ArrayList<>();

        pA.getNextToken();

        switch (pA.curToken().lexeme) {

            case "SELECT" -> SelectParser.analyseSelect(pA, children, false);

            default -> throw new SQLParseException("Invalid query keyword!");
        }

        if (pA.isEnd()) {

            throw new SQLParseException(String.format("Expected end of query on %d!",
                    pA.curTokenPos()));
        }

        return new Node(NodeType.QUERY, children);
    }

}
