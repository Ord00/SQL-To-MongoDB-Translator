package sql.to.mongodb.translator.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.CodeGenerator;
import sql.to.mongodb.translator.IRGenerator;
import sql.to.mongodb.translator.Parser;
import exceptions.SQLParseException;
import sql.to.mongodb.translator.exceptions.SQLScanException;
import sql.to.mongodb.translator.Scanner;
import sql.to.mongodb.translator.dto.AnalysisResult;
import sql.to.mongodb.translator.dto.SqlRequest;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8080")
public class AnalysisController {

    private final Scanner scanner;
    private final Parser parser;
    private final IRGenerator irGenerator;
    private final CodeGenerator codeGenerator;

    @Autowired
    public AnalysisController(Scanner scanner,
                              Parser parser,
                              IRGenerator irGenerator,
                              CodeGenerator codeGenerator) {
        this.scanner = scanner;
        this.parser = parser;
        this.irGenerator = irGenerator;
        this.codeGenerator = codeGenerator;
    }

    @PostMapping("/analyse")
    public ResponseEntity<?> analyseSql(@RequestBody SqlRequest request) {

        try {
            List<Token> lexicalResult = new ArrayList<>();

            // Лексический анализ
            scanner.tryAnalyse(request.sqlQuery(), lexicalResult);

            // Синтаксический анализ
            Node syntaxResult = parser.tryAnalyse(lexicalResult);

            // Генерация промежуточного представления
            SqlToMongoIR ir = irGenerator.generateIR(syntaxResult);

            // Генерация MongoDB кода
            String mongoCode = codeGenerator.generate(ir);

            return ResponseEntity.ok(new AnalysisResult(lexicalResult, syntaxResult, ir, mongoCode));

        } catch (SQLScanException| SQLParseException | CodeGenerationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}
