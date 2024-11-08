package org.example.entities;

import org.example.entities.builders.*;
import org.example.entities.finite.automata.FSM;
import org.example.enums.Category;
import org.example.interfaces.LexicallyAnalysable;

import java.util.*;

public class Scanner implements LexicallyAnalysable {
    private final Map<Category, FSM> fsms;

    public Scanner() {
        fsms = new HashMap<>();

        Map<Category, AbstractFSMBuilder> builders = new HashMap<>() {{
            put(Category.OPERATOR, new OperatorFSMBuilder());
            put(Category.KEYWORD, new KeywordFSMBuilder());
            put(Category.IDENTIFIER, new IdentifierFSMBuilder());
            put(Category.NUMBER, new NumberFSMBuilder());
            put(Category.LITERAL, new LiteralFSMBuilder());
            put(Category.PUNCTUATION, new PunctuationFSMBuilder());
            put(Category.ALL, new AllFSMBuilder());
        }};

        for (Category category : Category.values()) {
            AbstractFSMBuilder fsmBuilder = builders.get(category);
            fsms.put(category, fsmBuilder.build());
        }
    }

    public Boolean tryAnalyse(String codeToScan, List<Token> tokens, List<String> errors) {

        List<String> partsSql = parsePart(codeToScan.trim());
        for (String split : partsSql) {
            boolean isFound = false;
            for (Category category : fsms.keySet()) {
                FSM fsm = fsms.get(category);
                if (fsm.simulate(split)) {
                    tokens.add(new Token(split, category));
                    isFound = true;
                    break;
                }
            }

            if (!isFound) {
                errors.add(String.format("The lexeme %s is not recognised by the language!", split));
            }
        }

        return errors.isEmpty();
    }

    private List<String> parsePart(String part) {
        List<String> specials = new ArrayList<>(List.of(
                " ",
                ",",
                "\\.",
                "\\(",
                "\\)",
                "<",
                ">",
                "="
        ));

        List<String> tempParts = new ArrayList<>();
        tempParts.add(part);

        for (String special : specials) {
            String[] temps = tempParts.toArray(new String[0]);
            tempParts.clear();

            for (String t : temps) {
                String[] splits = t.trim().split(special, -1);

                if (splits.length == 1) {
                    tempParts.add(splits[0]);
                } else {
                    for (String split : splits) {
                        tempParts.add(split);
                        tempParts.add(special.replaceAll("\\\\", ""));
                    }
                    tempParts.removeLast();
                }

                tempParts = new ArrayList<>(tempParts.stream()
                        .map(String::trim)
                        .filter(i -> !i.isBlank())
                        .toList());
            }
        }

        return tempParts;
    }
}
