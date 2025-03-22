package sql.to.mongodb.translator.service.parser;

public class ParserResult {

    private Node parseTree;
    private boolean isComplicatedQuery;
    private boolean isComplicatedWhere;

    public ParserResult(Node parseTree, boolean isComplicatedQuery, boolean isComplicatedWhere) {
        this.parseTree = parseTree;
        this.isComplicatedQuery = isComplicatedQuery;
        this.isComplicatedWhere = isComplicatedWhere;
    }

    public Node getParseTree() {
        return parseTree;
    }

    public void setParseTree(Node parseTree) {
        this.parseTree = parseTree;
    }

    public boolean isComplicatedQuery() {
        return isComplicatedQuery;
    }

    public void setComplicatedQuery(boolean complicatedQuery) {
        isComplicatedQuery = complicatedQuery;
    }

    public boolean isComplicatedWhere() {
        return isComplicatedWhere;
    }

    public void setComplicatedWhere(boolean complicatedWhere) {
        isComplicatedWhere = complicatedWhere;
    }
}
