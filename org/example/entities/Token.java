package org.example.entities;

import org.example.enums.Category;

public class Token {

    public String lexeme;

    public Category category;

    public Token(String lexeme, Category category) {
        this.lexeme = lexeme;
        this.category = category;
    }

    @Override
    public String toString() {
        return String.format("(%s|%s)", category.toString(), lexeme);
    }
}
