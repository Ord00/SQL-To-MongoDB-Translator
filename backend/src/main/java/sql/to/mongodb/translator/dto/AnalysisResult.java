package sql.to.mongodb.translator.dto;

import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.List;

public record AnalysisResult(List<Token> lexicalResult,
                             Node syntaxResult,
                             SqlToMongoIR ir,
                             String mongoCode) {}
