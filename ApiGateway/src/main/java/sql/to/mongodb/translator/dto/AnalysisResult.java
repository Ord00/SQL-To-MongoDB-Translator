package sql.to.mongodb.translator.dto;

import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.List;

public record AnalysisResult(List<Token> lexicalResult,
                             Node syntaxResult,
                             SqlToMongoIR ir,
                             String mongoCode) {}
