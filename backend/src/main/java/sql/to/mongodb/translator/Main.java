package sql.to.mongodb.translator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {

        SpringApplication.run(Main.class, args);
//        Scanner scanner = new Scanner();
//        List<Token> tokens = new ArrayList<>();
//        List<String> errors = new ArrayList<>();
//
//        scanner.tryAnalyse("""
//                SELECT a, b, c, d FROM collection""", tokens, errors);
//
//        System.out.println("\nResult of scanner:\n");
//        System.out.println(tokens);
//
//        System.out.println("\nResult of parser:\n");
//
//        Parser parser = new Parser(tokens, errors);
//        Node parserRes = parser.tryAnalyse();
//        System.out.println(parserRes);
//
//        System.out.println("\nResult of code generator:\n");
//
//        CodeGenerator codeGenerator = new CodeGenerator(parserRes, false);
//        System.out.println(codeGenerator.generateCode());

    }
}