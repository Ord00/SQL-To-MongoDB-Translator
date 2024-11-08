package sql.to.mongodb.translator;

import sql.to.mongodb.translator.entities.Scanner;
import sql.to.mongodb.translator.entities.Token;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        // SELECT * FROM Students WHERE Id > 2 AND K LIKE 'mou%_se'
        // SELECT id, ss, ff FROM products WHERE id IN (SELECT product_id FROM sales)
        scanner.tryAnalyse("SELECT id, ss, ff FROM products WHERE id IN (SELECT product_id FROM sales)", tokens, errors);
        System.out.println(tokens);
    }
}