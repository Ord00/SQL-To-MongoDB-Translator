package sql.to.mongodb.translator.interfaces;

import sql.to.mongodb.translator.exceptions.SQLScanException;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public interface LexicallyAnalysable {
    void tryAnalyse(String codeToScan, List<Token> tokens) throws SQLScanException;
}
