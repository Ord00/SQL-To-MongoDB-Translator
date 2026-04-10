package sql.to.mongodb.translator.responses;

import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public record AnalysisResult(List<Token> lexicalResult,
                             Node syntaxResult,
                             SqlToMongoIR ir,
                             String mongoCode,
                             ErrorResponse errorResponse) {
    public static AnalysisResult success(List<Token> lexicalResult,
                                         Node syntaxResult,
                                         SqlToMongoIR ir,
                                         String mongoCode) {
        return new AnalysisResult(
                lexicalResult,
                syntaxResult,
                ir,
                mongoCode,
                null);
    }

    public static AnalysisResult error(String message, int code) {
        return new AnalysisResult(
                null,
                null,
                null,
                null,
                new ErrorResponse(message, code));
    }

    public boolean isSuccess() {
        return errorResponse == null;
    }
}
