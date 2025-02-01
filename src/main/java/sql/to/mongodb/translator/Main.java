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
        scanner.tryAnalyse("SELECT f, g, t FROM Students S INNER JOIN Faculties AS F ON S.Id = F.Id WHERE Id >= -52", tokens, errors);
        System.out.println(tokens);

        Parser parser = new Parser(tokens, errors);
        parser.tryAnalyse(false);
    }
}