package org.example;

import org.example.entities.Scanner;
import org.example.entities.Token;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        // SELECT * FROM Students WHERE Id > 2 AND K LIKE 'mouse'
        scanner.tryAnalyse("SELECT * FROM products WHERE id IN (SELECT productid FROM sales)", tokens, errors);
        System.out.println(tokens);
    }
}