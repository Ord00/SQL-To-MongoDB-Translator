package sql.to.mongodb.translator;

import sql.to.mongodb.translator.code.generator.CodeGenerator;
import sql.to.mongodb.translator.exceptions.SQLParseException;
import sql.to.mongodb.translator.exceptions.SQLScanException;
import sql.to.mongodb.translator.exceptions.TranslateToMQLException;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Scanner;
import sql.to.mongodb.translator.scanner.Token;

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

            CodeGenerator codeGenerator = new CodeGenerator(parseTree, false);

            return codeGenerator.generateCode();

        } catch (SQLScanException | SQLParseException e) {

            throw new TranslateToMQLException("Incorrect SQL query for translation");

        }
    }

}
