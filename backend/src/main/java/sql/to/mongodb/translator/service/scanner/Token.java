package sql.to.mongodb.translator.service.scanner;

import sql.to.mongodb.translator.service.enums.Category;

public class Token {

    public String lexeme;

    public Category category;

    public Token(String lexeme, Category category) {
        this.lexeme = lexeme;
        this.category = category;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Token other = (Token) obj;

        if (category.equals(Category.IDENTIFIER)
                || category.equals(Category.LITERAL)
                || category.equals(Category.NUMBER)) {
            return category.equals(other.category);
        }

        return lexeme.equals(other.lexeme) && category.equals(other.category);
    }

    @Override
    public String toString() {
        return String.format("(%s|%s)", category.toString(), lexeme);
    }
}
