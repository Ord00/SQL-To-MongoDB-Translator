package sql.to.mongodb.translator.entities;

import sql.to.mongodb.translator.enums.Category;

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
        return lexeme.equals(other.lexeme) && category.equals(other.category);
    }

    @Override
    public String toString() {
        return String.format("(%s|%s)", category.toString(), lexeme);
    }
}
