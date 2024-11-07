package org.example.interfaces;

import org.example.entities.Token;

import java.util.List;

public interface LexicallyAnalysable {
    Boolean tryAnalyse(String codeToScan, List<Token> tokens, List<String> errors);
}
