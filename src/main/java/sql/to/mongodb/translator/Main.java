package sql.to.mongodb.translator;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Scanner;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.parser.Parser;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        scanner.tryAnalyse("""
                SELECT CompetitionName, Race.*
                FROM Competition LEFT JOIN Race
                	 ON Id_competition = Competition""", tokens, errors);

        System.out.println("\nResult of scanner:\n");
        System.out.println(tokens);

        System.out.println("\nResult of parser:\n");

        Parser parser = new Parser(tokens, errors);
        Node parserRes = parser.tryAnalyse(false);
        System.out.println(parserRes);

    }
}