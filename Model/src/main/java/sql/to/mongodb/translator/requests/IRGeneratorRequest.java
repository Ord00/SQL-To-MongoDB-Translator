package sql.to.mongodb.translator.requests;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public record IRGeneratorRequest(List<Token> lexicalResult,
                                 Node syntaxResult) {}
