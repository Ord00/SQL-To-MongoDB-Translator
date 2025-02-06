package sql.to.mongodb.translator;

import sql.to.mongodb.translator.entities.Scanner;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.parser.Parser;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT SUM(Namesakes.PeopleCount) AS NamesakesCount\n" +
                "FROM (SELECT COUNT(*) AS PeopleCount\n" +
                "\t FROM Staff\n" +
                "\t GROUP BY LastName\n" +
                "\t HAVING COUNT(*) > 1\n" +
                "\t ) AS Namesakes", tokens, errors);
        System.out.println(tokens);

        Parser parser = new Parser(tokens, errors);
        parser.tryAnalyse(false);
    }
}