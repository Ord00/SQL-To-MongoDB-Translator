package sql.to.mongodb.translator.entities;

import sql.to.mongodb.translator.entities.builders.*;
import sql.to.mongodb.translator.entities.builders.special.words.AggregateFSMBuilder;
import sql.to.mongodb.translator.entities.builders.special.words.FunctionFSMBuilder;
import sql.to.mongodb.translator.entities.builders.special.words.KeywordFSMBuilder;
import sql.to.mongodb.translator.entities.finite.automata.FSM;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.interfaces.LexicallyAnalysable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class Scanner implements LexicallyAnalysable {
    private final Map<Category, FSM> fsms;

    public Scanner() {
        fsms = new LinkedHashMap<>();

        Map<Category, FSMBuilder> builders = new LinkedHashMap<>() {{
            put(Category.OPERATOR, new OperatorFSMBuilder());
            put(Category.KEYWORD, new KeywordFSMBuilder());
            put(Category.AGGREGATE, new AggregateFSMBuilder());
            put(Category.FUNCTION, new FunctionFSMBuilder());
            put(Category.IDENTIFIER, new IdentifierFSMBuilder());
            put(Category.NUMBER, new NumberFSMBuilder());
            put(Category.LITERAL, new LiteralFSMBuilder());
            put(Category.PUNCTUATION, new PunctuationFSMBuilder());
            put(Category.ALL, new AllFSMBuilder());
        }};

        for (Category category : Category.values()) {
            FSMBuilder fsmBuilder = builders.get(category);
            fsms.put(category, fsmBuilder.build());
        }
    }

    public Boolean tryAnalyse(String codeToScan, List<Token> tokens, List<String> errors) {

        List<String> partsSql = splitIntoParts(codeToScan.trim());

        for (String part : partsSql) {

            boolean isFound = false;

            for (Category category : fsms.keySet()) {

                FSM fsm = fsms.get(category);

                if (fsm.simulate(part)) {
                    tokens.add(new Token(part, category));
                    isFound = true;
                    break;
                }
            }

            if (!isFound) {
                errors.add(String.format("The lexeme %s is not recognised by the language!", part));
            }
        }

        return errors.isEmpty();
    }

    private List<String> splitIntoParts(String codeToSplit) {

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

        List<String> parts = new ArrayList<>();
        parts.add(codeToSplit);

        for (String special : specials) {

            String[] curParts = parts.toArray(new String[0]);
            parts.clear();

            for (String part : curParts) {

                String[] splits = part.trim().split(String.format("%s(?=(?:[^']|'[^']*')*[^']*$)", special), -1);

                if (splits.length == 1) {
                    parts.add(splits[0]);
                } else {
                    for (String split : splits) {
                        parts.add(split);
                        parts.add(special.replaceAll("\\\\", ""));
                    }
                    parts.removeLast();
                }

                parts = new ArrayList<>(parts.stream()
                        .map(String::trim)
                        .filter(i -> !i.isBlank())
                        .toList());
            }
        }

        return parts;
    }
}
