package sql.to.mongodb.translator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sql.to.mongodb.translator.entities.Scanner;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;

import java.util.ArrayList;
import java.util.List;

public class ScannerTest {
    private static final Scanner SCANNER = new Scanner();

    @Test
    public void testSubqueryWithIn() {

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

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        SCANNER.tryAnalyse("SELECT id, name, file FROM products WHERE id IN (SELECT product_id FROM sales)", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }

    @Test
    public void testConditionWhereWithNumbersAndStrings() {

        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.KEYWORD),
                new Token("*", Category.ALL),
                new Token("FROM", Category.KEYWORD),
                new Token("Students", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("Id", Category.IDENTIFIER),
                new Token(">", Category.OPERATOR),
                new Token("2", Category.NUMBER),
                new Token("AND", Category.KEYWORD),
                new Token("K", Category.IDENTIFIER),
                new Token("LIKE", Category.OPERATOR),
                new Token("'mou%_se'", Category.LITERAL)
        ));

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        SCANNER.tryAnalyse("SELECT * FROM Students WHERE Id > 2 AND K LIKE 'mou%_se'", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }

    @Test
    public void testFunctions() {

        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.KEYWORD),
                new Token("COUNT", Category.FUNCTION),
                new Token("(", Category.PUNCTUATION),
                new Token("DISTINCT", Category.KEYWORD),
                new Token("Id_book", Category.IDENTIFIER),
                new Token(")", Category.PUNCTUATION),
                new Token("FROM", Category.KEYWORD),
                new Token("Library", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("Id_book", Category.IDENTIFIER),
                new Token("IN", Category.KEYWORD),
                new Token("(", Category.PUNCTUATION),
                new Token("7", Category.NUMBER),
                new Token(",", Category.PUNCTUATION),
                new Token("19", Category.NUMBER),
                new Token(")", Category.PUNCTUATION)
        ));

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        SCANNER.tryAnalyse("SELECT COUNT(DISTINCT Id_book) FROM Library WHERE Id_book IN(7, 19)", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }
}
