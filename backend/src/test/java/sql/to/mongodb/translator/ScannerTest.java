package sql.to.mongodb.translator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sql.to.mongodb.translator.service.scanner.Scanner;
import sql.to.mongodb.translator.service.scanner.Token;
import sql.to.mongodb.translator.service.enums.Category;

import java.util.ArrayList;
import java.util.List;

public class ScannerTest {
    private static final Scanner scanner = new Scanner();

    @Test
    public void testSubqueryWithIn() {

        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.DML),
                new Token("id", Category.IDENTIFIER),
                new Token(",", Category.PUNCTUATION),
                new Token("name", Category.IDENTIFIER),
                new Token(",", Category.PUNCTUATION),
                new Token("file", Category.IDENTIFIER),
                new Token("FROM", Category.KEYWORD),
                new Token("products", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("id", Category.IDENTIFIER),
                new Token("IN", Category.LOGICAL_EXPRESSION),
                new Token("(", Category.PUNCTUATION),
                new Token("SELECT", Category.DML),
                new Token("product_id", Category.IDENTIFIER),
                new Token("FROM", Category.KEYWORD),
                new Token("sales", Category.IDENTIFIER),
                new Token(")", Category.PUNCTUATION)
        ));

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT id, name, file FROM products WHERE id IN (SELECT product_id FROM sales)", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }

    @Test
    public void testConditionWhereWithNumbersAndStrings() {

        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.DML),
                new Token("*", Category.ALL),
                new Token("FROM", Category.KEYWORD),
                new Token("Students", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("Id", Category.IDENTIFIER),
                new Token(">", Category.LOGICAL_OPERATOR),
                new Token("=", Category.LOGICAL_OPERATOR),
                new Token("2", Category.NUMBER),
                new Token("/", Category.ARITHMETIC_OPERATOR),
                new Token("7", Category.NUMBER),
                new Token("AND", Category.LOGICAL_COMBINE),
                new Token("K", Category.IDENTIFIER),
                new Token("LIKE", Category.LOGICAL_EXPRESSION),
                new Token("'mou%_se'", Category.LITERAL)
        ));

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT * FROM Students WHERE Id >= 2 / 7 AND K LIKE 'mou%_se'", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }

    @Test
    public void testAggregateFunctions() {

        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.DML),
                new Token("COUNT", Category.AGGREGATE),
                new Token("(", Category.PUNCTUATION),
                new Token("DISTINCT", Category.KEYWORD),
                new Token("Id_book", Category.IDENTIFIER),
                new Token(")", Category.PUNCTUATION),
                new Token("FROM", Category.KEYWORD),
                new Token("Library", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("Id_book", Category.IDENTIFIER),
                new Token("IN", Category.LOGICAL_EXPRESSION),
                new Token("(", Category.PUNCTUATION),
                new Token("7", Category.NUMBER),
                new Token(",", Category.PUNCTUATION),
                new Token("19", Category.NUMBER),
                new Token(")", Category.PUNCTUATION)
        ));

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT COUNT(DISTINCT Id_book) FROM Library WHERE Id_book IN(7, 19)", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }

    @Test
    public void testLiteralsRecognition() {

        List<Token> expectedTokens = new ArrayList<>(List.of(
                new Token("SELECT", Category.DML),
                new Token("*", Category.ALL),
                new Token("FROM", Category.KEYWORD),
                new Token("Students", Category.IDENTIFIER),
                new Token("WHERE", Category.KEYWORD),
                new Token("Id", Category.IDENTIFIER),
                new Token(">", Category.LOGICAL_OPERATOR),
                new Token("'.2#, '", Category.LITERAL),
                new Token("AND", Category.LOGICAL_COMBINE),
                new Token("K", Category.IDENTIFIER),
                new Token("LIKE", Category.LOGICAL_EXPRESSION),
                new Token("'mou %_se'", Category.LITERAL)
        ));

        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT * FROM Students WHERE Id > '.2#, ' AND K LIKE 'mou %_se'", tokens, errors);
        Assertions.assertEquals(expectedTokens, tokens);
    }
}
