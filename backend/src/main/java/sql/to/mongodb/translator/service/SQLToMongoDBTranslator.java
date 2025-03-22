package sql.to.mongodb.translator.service;

import sql.to.mongodb.translator.service.code.generator.CodeGenerator;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.exceptions.SQLScanException;
import sql.to.mongodb.translator.service.exceptions.TranslateToMQLException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.Parser;
import sql.to.mongodb.translator.service.parser.ParserResult;
import sql.to.mongodb.translator.service.scanner.Scanner;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

public class SQLToMongoDBTranslator {

    public String translate(String sqlQuery) throws TranslateToMQLException {

        try {

            Scanner scanner = new Scanner();
            List<Token> tokens = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            scanner.tryAnalyse(sqlQuery, tokens, errors);

            Parser parser = new Parser(tokens, errors);
            Node parseTree = parser.tryAnalyse();

            ParserResult parserResult = new ParserResult(parseTree,
                    false,
                    false);
            CodeGenerator codeGenerator = new CodeGenerator(parserResult);

            return codeGenerator.generateCode();

        } catch (SQLScanException | SQLParseException e) {

            throw new TranslateToMQLException("Incorrect SQL query for translation");

        }
    }

}
