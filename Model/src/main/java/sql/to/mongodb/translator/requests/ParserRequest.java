package sql.to.mongodb.translator.requests;

import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public record ParserRequest(List<Token> lexicalResult) {}
