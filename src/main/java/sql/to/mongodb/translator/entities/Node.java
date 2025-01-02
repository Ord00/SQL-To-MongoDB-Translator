package sql.to.mongodb.translator.entities;

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
}
