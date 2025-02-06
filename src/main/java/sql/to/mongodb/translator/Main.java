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
        scanner.tryAnalyse("SELECT MIN(TicketPrice) AS MinTicketPrice, MAX(TicketPrice) AS MaxTicketPrice FROM Race", tokens, errors);
        System.out.println(tokens);

        Parser parser = new Parser(tokens, errors);
        parser.tryAnalyse(false);
    }
}