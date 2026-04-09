package sql.to.mongodb.translator.processors;

import lombok.Getter;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.parser.NodeType;
import sql.to.mongodb.translator.scanner.Category;
import sql.to.mongodb.translator.scanner.Token;

import java.util.*;

public class CorrelationAnalyzer {

    private final Set<String> outerTables = new HashSet<>();
    private final Map<String, String> outerAliases = new HashMap<>();
    @Getter
    private final List<CorrelationCondition> correlations = new ArrayList<>();

    public void analyzeForCorrelations(Node subqueryNode,
                                       Set<String> parentTables,
                                       Map<String, String> parentAliases) {
        if (subqueryNode == null || parentTables == null) {
            return;
        }

        this.outerTables.addAll(parentTables);
        if (parentAliases != null) {
            this.outerAliases.putAll(parentAliases);
        }

        correlations.clear();
        findCorrelationsInNode(subqueryNode);
    }

    private void findCorrelationsInNode(Node node) {
        if (node == null) return;

        // Проверяем идентификаторы на корреляцию
        if (node.getNodeType() == NodeType.IDENTIFIER) {
            checkIdentifierForCorrelation(node);
        }

        // Проверяем логические условия
        if (node.getNodeType() == NodeType.LOGICAL_CHECK) {
            processLogicalCheck(node);
        }

        // Рекурсивно обходим детей
        if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                findCorrelationsInNode(child);
            }
        }
    }

    private void processLogicalCheck(Node logicalCheckNode) {
        if (logicalCheckNode.getChildren() == null) return;

        String operator = null;
        List<String> leftIdentifiers = new ArrayList<>();
        List<String> rightIdentifiers = new ArrayList<>();
        boolean processingLeft = true;

        for (Node child : logicalCheckNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                Token token = child.getToken();
                if (token.category == Category.LOGICAL_OPERATOR) {
                    operator = token.lexeme;
                    processingLeft = false;
                } else if (isComparisonOperator(token.lexeme)) {
                    operator = token.lexeme;
                    processingLeft = false;
                }
            } else if (child.getNodeType() == NodeType.IDENTIFIER) {
                String identifier = buildIdentifierString(child);
                if (processingLeft) {
                    leftIdentifiers.add(identifier);
                } else {
                    rightIdentifiers.add(identifier);
                }
            } else if (child.getNodeType() == NodeType.ARITHMETIC_EXP) {
                // Из арифметического выражения извлекаем поля
                List<String> fields = extractFieldsFromArithmetic(child);
                if (processingLeft) {
                    leftIdentifiers.addAll(fields);
                } else {
                    rightIdentifiers.addAll(fields);
                }
            }
        }

        // Проверяем корреляцию между левыми и правыми операндами
        if (operator != null && (!leftIdentifiers.isEmpty() || !rightIdentifiers.isEmpty())) {
            for (String left : leftIdentifiers) {
                for (String right : rightIdentifiers) {
                    if (isCorrelationPair(left, right)) {
                        addCorrelation(left, right, operator);
                    }
                }
            }
        }
    }

    /**
     * Извлечение полей из арифметического выражения
     */
    private List<String> extractFieldsFromArithmetic(Node arithNode) {
        List<String> fields = new ArrayList<>();
        if (arithNode == null) return fields;

        if (arithNode.getNodeType() == NodeType.IDENTIFIER) {
            String identifier = buildIdentifierString(arithNode);
            if (identifier.contains(".")) {
                fields.add(identifier);
            }
        }

        if (arithNode.getChildren() != null) {
            for (Node child : arithNode.getChildren()) {
                fields.addAll(extractFieldsFromArithmetic(child));
            }
        }

        return fields;
    }

    private void checkIdentifierForCorrelation(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2) {
            return;
        }

        String tableOrAlias = extractTablePart(identifierNode);
        String column = extractColumnPart(identifierNode);

        if (tableOrAlias == null || column == null) {
            return;
        }

        // Проверяем, является ли таблица/алиас внешним
        boolean isExternalReference = false;
        String resolvedTable = tableOrAlias;

        if (outerTables.contains(tableOrAlias) || outerAliases.containsKey(tableOrAlias)) {
            isExternalReference = true;
        } else if (tableOrAlias.contains(".")) {
            String[] parts = tableOrAlias.split("\\.");
            String tableName = parts[parts.length - 1];
            if (outerTables.contains(tableName)) {
                isExternalReference = true;
                resolvedTable = tableName;
            }
        }

        if (isExternalReference) {
            Field outerField = new Field();
            outerField.setSource(resolvedTable);
            outerField.setField(column);

            Field innerField = new Field();
            innerField.setField(column);

            CorrelationCondition correlation = new CorrelationCondition(outerField, innerField, "=");

            if (containsCorrelation(correlation)) {
                correlations.add(correlation);
            }
        }
    }

    /**
     * Проверка, образуют ли два идентификатора корреляцию
     */
    private boolean isCorrelationPair(String left, String right) {
        boolean leftIsExternal = isExternalReference(left);
        boolean rightIsExternal = isExternalReference(right);

        // Корреляция - когда один внешний, а другой внутренний
        return leftIsExternal != rightIsExternal;
    }

    /**
     * Добавление корреляции
     */
    private void addCorrelation(String left, String right, String operator) {
        // Определяем, какой из них внешний
        boolean leftIsExternal = isExternalReference(left);

        String outerIdentifier = leftIsExternal ? left : right;
        String innerIdentifier = leftIsExternal ? right : left;

        // Извлекаем таблицу и поле из внешнего идентификатора
        String[] outerParts = outerIdentifier.split("\\.");
        CorrelationCondition correlation = getCorrelationCondition(operator, outerParts, innerIdentifier);

        if (containsCorrelation(correlation)) {
            correlations.add(correlation);
        }
    }

    private static CorrelationCondition getCorrelationCondition(String operator,
                                                                String[] outerParts,
                                                                String innerIdentifier) {
        String outerTable = outerParts[0];
        String outerColumn = outerParts.length > 1 ? outerParts[1] : outerParts[0];

        // Внутренний идентификатор может быть без таблицы
        String[] innerParts = innerIdentifier.split("\\.");
        String innerColumn = innerParts.length > 1 ? innerParts[1] : innerParts[0];

        Field outerField = new Field();
        outerField.setSource(outerTable);
        outerField.setField(outerColumn);

        Field innerField = new Field();
        innerField.setField(innerColumn);

        return new CorrelationCondition(outerField, innerField, operator);
    }

    private String extractTablePart(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().isEmpty()) {
            return null;
        }
        Node firstChild = identifierNode.getChildren().getFirst();
        if (firstChild.getNodeType() == NodeType.TERMINAL) {
            return firstChild.getToken().lexeme;
        }
        return null;
    }

    private String extractColumnPart(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2) {
            return null;
        }
        Node secondChild = identifierNode.getChildren().get(1);
        if (secondChild.getNodeType() == NodeType.TERMINAL) {
            return secondChild.getToken().lexeme;
        }
        return null;
    }

    private boolean containsCorrelation(CorrelationCondition newCorrelation) {
        for (CorrelationCondition existing : correlations) {
            if (existing.getOuterField().toString().equals(newCorrelation.getOuterField().toString())
                    && existing.getInnerField().toString().equals(newCorrelation.getInnerField().toString())
                    && existing.getOperator().equals(newCorrelation.getOperator())) {
                return true; // Исправлено: если найден, возвращаем true
            }
        }
        return false; // Если не найден, возвращаем false
    }

    public List<CorrelationCondition> extractCorrelationConditions(Node whereNode) {
        List<CorrelationCondition> conditions = new ArrayList<>();
        if (whereNode == null) return conditions;

        // Очищаем предыдущие корреляции и находим новые
        correlations.clear();
        findCorrelationsInNode(whereNode);

        conditions.addAll(correlations);
        return conditions;
    }

    private boolean isComparisonOperator(String lexeme) {
        return lexeme.equals("=") || lexeme.equals("!=") || lexeme.equals("<") ||
                lexeme.equals(">") || lexeme.equals("<=") || lexeme.equals(">=");
    }

    private boolean isExternalReference(String identifier) {
        if (identifier == null) return false;

        // Если идентификатор не содержит точку, он не может быть внешней ссылкой
        // (внешняя ссылка всегда имеет вид table.column)
        if (!identifier.contains(".")) {
            return false;
        }

        String tablePart = extractTableFromIdentifier(identifier);
        return outerTables.contains(tablePart) || outerAliases.containsKey(tablePart);
    }

    private String extractTableFromIdentifier(String identifier) {
        if (!identifier.contains(".")) return identifier;
        String[] parts = identifier.split("\\.");
        return parts.length >= 2 ? parts[0] : identifier;
    }

    private String buildIdentifierString(Node identifierNode) {
        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Node child : identifierNode.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                sb.append(child.getToken().lexeme).append(".");
            }
        }
        if (!sb.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}