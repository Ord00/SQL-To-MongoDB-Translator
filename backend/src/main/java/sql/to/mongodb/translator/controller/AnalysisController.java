package sql.to.mongodb.translator.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sql.to.mongodb.translator.dto.AnalysisResult;
import sql.to.mongodb.translator.dto.SqlRequest;
import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.exceptions.SQLScanException;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.Parser;
import sql.to.mongodb.translator.service.scanner.Scanner;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8080")
public class AnalysisController {

    private Scanner scanner;
    private Parser parser;

    @Autowired
    public AnalysisController(Scanner scanner, Parser parser) {
        this.scanner = scanner;
        this.parser = parser;
    }

    @PostMapping("/analyse")
    public ResponseEntity<?> analyseSql(@RequestBody SqlRequest request) {

        try {
            List<Token> lexicalResult = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            // Лексический анализ
            scanner.tryAnalyse(request.getSqlQuery(), lexicalResult, errors);

            parser = new Parser(lexicalResult, errors);
            // Синтаксический анализ
            Node syntaxResult = parser.tryAnalyse();

            return ResponseEntity.ok(new AnalysisResult(lexicalResult, syntaxResult));

        } catch (SQLParseException | SQLScanException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}
