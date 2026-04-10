package sql.to.mongodb.translator.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sql.to.mongodb.translator.requests.ScannerRequest;
import sql.to.mongodb.translator.service.TranslateService;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8080")
@AllArgsConstructor
public class AnalysisController {

    private final TranslateService translateService;

    @PostMapping("/analyse")
    public CompletableFuture<ResponseEntity<?>> analyseSql(@RequestBody ScannerRequest request) {
        return translateService.translate(request)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(result.errorResponse().message());
                    }
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause();
                    String errorMessage = cause != null ? cause.getMessage() : throwable.getMessage();
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
                });
    }
}
