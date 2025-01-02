package sql.to.mongodb.translator;

import sql.to.mongodb.translator.entities.Scanner;
import sql.to.mongodb.translator.entities.Token;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT * FROM Students WHERE Id >= -52", tokens, errors);
        System.out.println(tokens);

        Parser parser = new Parser(tokens, errors);
        parser.tryAnalyse();
    }
}