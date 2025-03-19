package sql.to.mongodb.translator.dto;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public class AnalysisResult {

    private List<Token> lexicalResult;
    private Node syntaxResult;

    public AnalysisResult(List<Token> lexical, Node syntax) {

        this.lexicalResult = lexical;
        this.syntaxResult = syntax;

    }

    public List<Token> getLexicalResult() { return lexicalResult; }
    public Node getSyntaxResult() { return syntaxResult; }

}
