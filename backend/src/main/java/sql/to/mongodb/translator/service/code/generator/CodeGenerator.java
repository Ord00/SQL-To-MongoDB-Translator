package sql.to.mongodb.translator.service.code.generator;

import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.interfaces.ExpressionConvertible;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.parser.ParserResult;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.Iterator;
import java.util.List;

public class CodeGenerator {

    private Node parseTree;
    private boolean isComplicatedQuery;
    private boolean isComplicatedWhere;

    private Node curNode;

    public CodeGenerator(ParserResult parserResult) {

        this.parseTree = parserResult.getParseTree();
        this.isComplicatedQuery = parserResult.isComplicatedQuery();
        this.isComplicatedWhere = parserResult.isComplicatedWhere();

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

        String columnNames = convertColumns(iterator.next().getChildren().iterator());

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

        return String.format("%s.find({%s}%s)%s%s%s",
                from,
                where,
                columnNames,
                limit,
                skip,
                sort);

    }

    private String convertColumns(Iterator<Node> iterator) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                String lexeme = iterator.next().getToken().lexeme;
                return lexeme.equals("*") ? "" :
                        String.format(", {%s: 1%s}",
                                lexeme,
                                convertColumnsRec(iterator));

            }

        }

        return res;
    }

    private String convertColumnsRec(Iterator<Node> iterator) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                res = String.format(", %s: 1%s",
                        iterator.next().getToken().lexeme,
                        convertColumnsRec(iterator));

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

    private String convertLogicalOperator(String logOp) {

        return switch (logOp) {
            case "=": yield "$eq";
            case "<>": yield "$ne";
            case "<": yield "$lt";
            case ">": yield  "$gt";
            case "<=": yield "$lte";
            case ">=": yield "$gte";
            case "IN": yield "$in";
            default: yield "$nin";
        };
    }

    private String convertLogicalCheck(Iterator<Node> iterator) {

        String res = "";

        if (!isComplicatedQuery) {

            String logOp = convertLogicalOperator(iterator.next().getToken().lexeme);

            if (!isComplicatedWhere) {

                res = String.format("{%s: %s}",
                        logOp,
                        iterator.next().getToken().lexeme);

            }
        }

        return res;
    }

    private String convertWhereRec(Iterator<Node> iterator) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                Iterator<Node> logCheck = iterator.next().getChildren().iterator();

                if (!isComplicatedWhere) {

                    return String.format(", %s: %s%s",
                            logCheck.next().getToken().lexeme,
                            convertLogicalCheck(logCheck),
                            convertWhere(iterator));


                }
            }

        }

        return res;
    }

    private String convertWhere(Iterator<Node> iterator) {

        String res = "";

        if (iterator.hasNext()) {

            if (!isComplicatedQuery) {

                Iterator<Node> logCheck = iterator.next().getChildren().iterator();

                return String.format("%s: %s%s",
                        logCheck.next().getToken().lexeme,
                        convertLogicalCheck(logCheck),
                        convertWhereRec(iterator));

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
