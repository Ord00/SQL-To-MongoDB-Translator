package sql.to.mongodb.translator.scanner;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.scanner.builders.*;
import sql.to.mongodb.translator.scanner.builders.special.words.*;
import sql.to.mongodb.translator.scanner.finite.automata.FSM;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.interfaces.LexicallyAnalysable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Scanner implements LexicallyAnalysable {
    private final Map<Category, FSM> fsms;

    public Scanner() {
        fsms = new LinkedHashMap<>();

        Map<Category, FSMBuilder> builders = new LinkedHashMap<>() {{
            put(Category.NOT, new NotFSMBuilder());
            put(Category.LOGICAL_COMBINE, new LogicalCombineFSMBuilder());
            put(Category.LOGICAL_EXPRESSION, new LogicalExpressionFSMBuilder());
            put(Category.LOGICAL_OPERATOR, new LogicalOperatorFSMBuilder());
            put(Category.ARITHMETIC_OPERATOR, new ArithmeticOperatorFSMBuilder());
            put(Category.DDL, new DdlFSMBuilder());
            put(Category.DML, new DmlFSMBuilder());
            put(Category.DCL, new DclFSMBuilder());
            put(Category.TCL, new TclFSMBuilder());
            put(Category.KEYWORD, new KeywordFSMBuilder());
            put(Category.AGGREGATE, new AggregateFSMBuilder());
            put(Category.FUNCTION, new FunctionFSMBuilder());
            put(Category.NULL, new NullFSMBuilder());
            put(Category.TYPE, new TypeFSMBuilder());
            put(Category.OBJECT, new ObjectFSMBuilder());
            put(Category.IDENTIFIER, new IdentifierFSMBuilder());
            put(Category.NUMBER, new NumberFSMBuilder());
            put(Category.LITERAL, new LiteralFSMBuilder());
            put(Category.PUNCTUATION, new PunctuationFSMBuilder());
            put(Category.ALL, new AllFSMBuilder());
        }};

        Category[] categories = Category.values();
        int len = categories.length;

        for (int i = 0; i < len - 2; ++i) {
            FSMBuilder fsmBuilder = builders.get(categories[i]);
            fsms.put(categories[i], fsmBuilder.build());
        }
    }

    public Boolean tryAnalyse(String codeToScan,
                              List<Token> tokens,
                              List<String> errors) {

        List<String> partsSql = splitIntoParts(codeToScan.trim());

        for (String part : partsSql) {

            boolean isFound = false;

            for (Category category : fsms.keySet()) {

                FSM fsm = fsms.get(category);

                if (fsm.simulate(part)) {

                    tokens.add(new Token(List.of(Category.DDL,
                            Category.DML,
                            Category.DCL,
                            Category.TCL,
                            Category.KEYWORD,
                            Category.AGGREGATE,
                            Category.FUNCTION,
                            Category.LOGICAL_COMBINE,
                            Category.LOGICAL_EXPRESSION,
                            Category.LOGICAL_COMBINE,
                            Category.LOGICAL_EXPRESSION,
                            Category.TYPE,
                            Category.OBJECT,
                            Category.NOT).contains(category) ? part.toUpperCase() : part, category));
                    isFound = true;
                    break;
                }
            }

            if (!isFound) {
                errors.add(String.format("The lexeme %s is not recognised by the language!",
                        part));
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
                "=",
                "\n",
                "\t"
        ));

        List<String> parts = new ArrayList<>();
        parts.add(codeToSplit);

        for (String special : specials) {

            String[] curParts = parts.toArray(new String[0]);
            parts.clear();

            for (String part : curParts) {

                String[] splits = part.trim().split(String.format("%s(?=(?:[^']|'[^']*')*[^']*$)",
                        special),
                        -1);

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
