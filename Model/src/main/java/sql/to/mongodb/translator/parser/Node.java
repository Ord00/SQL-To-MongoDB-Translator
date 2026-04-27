package sql.to.mongodb.translator.parser;

import lombok.Getter;
import lombok.NoArgsConstructor;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
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

    @Override
    public String toString() {

        String res;

        if (children.isEmpty()) {

            res = String.format("(%s|%s)", nodeType.toString(), token);

        } else {

            res = String.format("{%s|%s}", nodeType.toString(), children);

        }
        return res;
    }
}
