package sql.to.mongodb.translator.parser;

import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.enums.NodeType;

import java.util.ArrayList;
import java.util.List;

public class Node {
    private NodeType nodeType;
    private List<Node> children;
    private Token token;

    public Node(NodeType nodeType, List<Node> children) {
        this.nodeType = nodeType;
        this.children = children;
    }

    public Node(NodeType nodeType, Token token) {
        this.nodeType = nodeType;
        this.children = new ArrayList<>();
        this.token = token;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public List<Node> getChildren() {
        return children;
    }

    @Override
    public String toString() {

        String res;

        if (children.isEmpty()) {

            res = String.format("(%s|%s)", nodeType.toString(), token);

        } else {

            res = String.format("{%s|%s}", nodeType.toString(), children.toString());

        }
        return res;
    }
}
