package org.example;

import org.example.entities.Scanner;
import org.example.entities.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        scanner.tryAnalyse("SELECT * FROM Students WHERE Id > 2 AND K LIKE 'mouse'", tokens, errors);
        System.out.println(tokens);
/*        String splitTest = "Select";
        System.out.println(Arrays.toString(splitTest.split(",")));*/
    }
}