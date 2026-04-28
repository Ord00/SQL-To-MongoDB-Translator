package sql.to.mongodb.translator.requests;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public record IRGeneratorRequest(@JsonTypeInfo(use = JsonTypeInfo.Id.NONE) List<Token> lexicalResult,
                                 Node syntaxResult) {}
