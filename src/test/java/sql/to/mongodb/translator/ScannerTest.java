package sql.to.mongodb.translator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sql.to.mongodb.translator.entities.Scanner;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;

import java.util.ArrayList;
import java.util.List;

public class ScannerTest {
    private static Scanner SCANNER = new Scanner();
    private static List<Token> TOKENS = new ArrayList<>();
    private static List<String> ERRORS = new ArrayList<>();

    @Test
    public void testFindMostPopularPlayers() {
        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.KEYWORD),
                new Token("id", Category.IDENTIFIER),
                new Token(",", Category.PUNCTUATION),
                new Token("name", Category.IDENTIFIER),
                new Token(",", Category.PUNCTUATION),
                new Token("file", Category.IDENTIFIER),
                new Token("FROM", Category.KEYWORD),
                new Token("products", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("id", Category.IDENTIFIER),
                new Token("IN", Category.KEYWORD),
                new Token("(", Category.PUNCTUATION),
                new Token("SELECT", Category.KEYWORD),
                new Token("product_id", Category.IDENTIFIER),
                new Token("FROM", Category.KEYWORD),
                new Token("sales", Category.IDENTIFIER),
                new Token(")", Category.PUNCTUATION)
        ));
        SCANNER.tryAnalyse("SELECT id, name, file FROM products WHERE id IN (SELECT product_id FROM sales)", TOKENS, ERRORS);
        Assertions.assertEquals(expectedTokens, TOKENS);
    }
}
