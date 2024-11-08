package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.entities.Token;

import java.util.List;

public interface LexicallyAnalysable {
    Boolean tryAnalyse(String codeToScan, List<Token> tokens, List<String> errors);
}