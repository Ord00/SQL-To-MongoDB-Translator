package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.entities.Node;
import sql.to.mongodb.translator.entities.Token;
import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class LogicalConditionParser extends Parser {
    public  LogicalConditionParser(List<Token> tokens, List<String> errors) {
        super(tokens, errors);
    }

    public static Node analyseLogicalCondition(List<Node> children) throws Exception {
        if (analyseOperand(children)) {
            if (curToken.lexeme.equals("NOT")) {
                getNextToken();
                children.add(terminal(t -> t.lexeme.equals("EXISTS")));
                children.add(terminal(t -> t.lexeme.equals("(")));
                children.add(tryAnalyse(true));
            } else if (curToken.lexeme.equals("EXISTS")) {
                getNextToken();
                children.add(terminal(t -> t.lexeme.equals("(")));
                children.add(tryAnalyse(true));
            } else {
                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
            }
        }

        if (curToken.category == Category.LOGICAL_COMBINE) {
            children.add(new Node(NodeType.TERMINAL, curToken));
            getNextToken();
            return analyseLogicalCondition(children);
        } else if (curTokenPos == tokens.size() - 1 || curToken.category == Category.KEYWORD) {
            return new Node(NodeType.LOGICAL_CONDITION, children);
        } else {
            throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
        }
    }

    public static boolean analyseOperand(List<Node> children) throws Exception {

        boolean isFound = true;

        if (curToken.category == Category.IDENTIFIER
                || curToken.category.equals(Category.NUMBER)
                || curToken.category.equals(Category.LITERAL)) {
            children.add(analyseOperation());
        } else if (curToken.lexeme.equals("(")) {

            children.add(tryAnalyse(true));
            children.add(analyseOperation());
        } else {
            isFound = false;
        }

        return !isFound;
    }

    public static boolean analyseOperator(List<Node> children) throws Exception {

        boolean isFound = true;

        if (curToken.lexeme.equals("=")) {

            if (analyseOperand(children)) {
                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
            }
        } else if (curToken.category == Category.LOGICAL_OPERATOR) {

        } else {
            isFound = false;
        }

        return isFound;
    }

    public static Node analyseOperation() throws Exception {
        List<Node> children = new ArrayList<>();

        if (curToken.lexeme.equals("=")) {

            if (analyseOperand(children)) {
                throw new Exception(String.format("Wrong first of column_names on %s", curTokenPos));
            }
        } else if (curToken.category == Category.LOGICAL_OPERATOR) {

        }

        return new Node(NodeType.LOGICAL_CONDITION, children);
    }
}
