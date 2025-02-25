package sql.to.mongodb.translator.code.generator;

import sql.to.mongodb.translator.enums.Category;
import sql.to.mongodb.translator.enums.NodeType;
import sql.to.mongodb.translator.interfaces.ExpressionConvertible;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.Iterator;
import java.util.List;

public class CodeGenerator {

    private Node parseTree;
    private boolean isComplicatedQuery;

    private Node curNode;

    public CodeGenerator(Node parseTree, boolean isComplicatedQuery) {

        this.parseTree = parseTree;
        this.isComplicatedQuery = isComplicatedQuery;

    }

    private void getNextNode(Iterator<Node> iterator) {

        if (iterator.hasNext()) {

            curNode = iterator.next();

        } else {

            curNode = new Node(NodeType.UNDEFINED, new Token("UNDEFINED", Category.UNDEFINED));

        }
    }

    private String convertExpression(String keyword,
                                     Iterator<Node> iterator,
                                     boolean isComplicatedStructure,
                                     ExpressionConvertible func) {

        String res = "";

        if (curNode.getToken().lexeme.equals(keyword)) {

            res = isComplicatedStructure ?
                    func.execute(iterator.next().getChildren().iterator()) :
                    func.execute(iterator);
            getNextNode(iterator);

        }

        return res;

    }

    public String generateCode() {

        List<Node> children = parseTree.getChildren();
        Iterator<Node> iterator = children.iterator();

        return switch (iterator.next().getToken().lexeme) {
            case "SELECT" -> convertSelect(iterator);
            default -> null;
        };
    }

    private String convertSelect(Iterator<Node> iterator) {

        String columnNames = convertColumns(iterator.next().getChildren().iterator(), true);

        iterator.next();
        String from = convertFrom(iterator.next().getChildren().iterator());

        getNextNode(iterator);

        String where = convertExpression("WHERE",
                iterator,
                true,
                this::convertWhere);

        String limit = convertExpression("LIMIT",
                iterator,
                false,
                this::convertLimit);

        String skip = convertExpression("OFFSET",
                iterator,
                false,
                this::convertOffset);

        String sort = convertExpression("ORDER",
                iterator,
                true,
                this::convertOrderBy);

        return String.format("%s.find({%s}, {%s})%s%s%s",
                from,
                where,
                columnNames,
                limit,
                skip,
                sort);

    }

    private String convertColumns(Iterator<Node> iterator, boolean isFirst) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                return String.format("%s%s: 1%s",
                        !isFirst ? ", " : "",
                        iterator.next().getToken().lexeme,
                        convertColumns(iterator, false));

            }

        }

        return res;
    }

    private String convertFrom(Iterator<Node> iterator) {

        String res = "";

        if (!isComplicatedQuery) {

            res = String.format("db.%s", convertTable(iterator.next().getChildren().iterator()));

        }

        return res;
    }

    private String convertTable(Iterator<Node> iterator) {

        String res = "";

        if (!isComplicatedQuery) {

            res = iterator.next().getToken().lexeme;

        }

        return res;
    }

    private String convertWhere(Iterator<Node> iterator) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                return null;

            }

        }

        return res;
    }

    private String convertLimit(Iterator<Node> iterator) {

        String res = "";

        if (!isComplicatedQuery) {

            res = String.format(".limit(%s)", iterator.next().getToken().lexeme);

        }

        return res;
    }

    private String convertOffset(Iterator<Node> iterator) {

        String res = "";

        if (!isComplicatedQuery) {

            res = String.format(".skip(%s)", iterator.next().getToken().lexeme);

        }

        return res;
    }

    private String convertOrderBy(Iterator<Node> iterator) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                return String.format(".skip({ %s })", convertOrderByRec(iterator, true));

            }

        }

        return res;
    }

    private String convertOrderByRec(Iterator<Node> iterator, boolean isFirst) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                return String.format("%s%s: %s%s",
                        !isFirst ? ", " : "",
                        iterator.next().getToken().lexeme,
                        iterator.next().getToken().lexeme.equals("ABS") ? 1 : -1,
                        convertOrderByRec(iterator, false));

            }

        }

        return res;
    }
}
