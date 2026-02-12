package sql.to.mongodb.translator.service.intermediate.representation;

import lombok.Getter;
import sql.to.mongodb.translator.service.enums.Category;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.parser.Node;
import sql.to.mongodb.translator.service.scanner.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CorrelationAnalyzer {

    private final Set<String> outerTables = new HashSet<>();
    private final Map<String, String> outerAliases = new HashMap<>();
    @Getter
    private final List<CorrelationCondition> correlations = new ArrayList<>();

    // Анализ подзапроса на наличие корреляций с внешними таблицами
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

        hasExternalReferences(subqueryNode);
    }

    // Проверка наличия ссылок на внешние таблицы
    private boolean hasExternalReferences(Node node) {
        if (node == null) {
            return false;
        }

        boolean hasCorrelations = false;

        if (node.getNodeType() == NodeType.IDENTIFIER) {
            hasCorrelations = checkIdentifierForCorrelation(node);
        }

        if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                if (hasExternalReferences(child)) {
                    hasCorrelations = true;
                }
            }
        }

        return hasCorrelations;
    }

    // Проверка идентификатора на корреляцию с внешними таблицами
    private boolean checkIdentifierForCorrelation(Node identifierNode) {

        if (identifierNode.getChildren() == null || identifierNode.getChildren().size() < 2) {
            return false;
        }

        String tableOrAlias = extractTablePart(identifierNode);
        String column = extractColumnPart(identifierNode);

        if (tableOrAlias == null || column == null) {
            return false;
        }

        // Проверяем, является ли таблица/алиас внешним
        boolean isExternalReference = false;

        // Совпадение с именем или алиасом внешней таблицы
        if (outerTables.contains(tableOrAlias) || outerAliases.containsKey(tableOrAlias)) {
            isExternalReference = true;
        }
        // Проверяем, является ли это квалифицированным именем внешней таблицы
        else if (tableOrAlias.contains(".")) {
            // Может быть schema.table или database.schema.table
            String[] parts = tableOrAlias.split("\\.");
            String tableName = parts[parts.length - 1];

            if (outerTables.contains(tableName)) {
                isExternalReference = true;
                tableOrAlias = tableName;
            }
        }

        if (isExternalReference) {

            CorrelationCondition correlation = new CorrelationCondition();
            correlation.setOuterField(tableOrAlias + "." + column);
            correlation.setInnerField(column); // В подзапросе может быть без квалификации
            correlation.setOperator("=");

            if (!containsCorrelation(correlation)) {
                correlations.add(correlation);
            }

            return true;
        }
        return false;
    }

    // Извлечение табличной части из идентификатора
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

    // Извлечение колоночной части из идентификатора
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

    // Проверка наличия дубликата условия корреляции
    private boolean containsCorrelation(CorrelationCondition newCorrelation) {

        for (CorrelationCondition existing : correlations) {

            if (existing.getOuterField().equals(newCorrelation.getOuterField())
                    && existing.getInnerField().equals(newCorrelation.getInnerField())) {
                return true;
            }
        }
        return false;
    }

    // Анализ WHERE условия на наличие условий корреляции
    public List<CorrelationCondition> extractCorrelationConditions(Node whereNode) {

        if (whereNode == null) {
            return new ArrayList<>();
        }

        List<CorrelationCondition> conditions = new ArrayList<>();
        extractCorrelationsFromNode(whereNode, conditions);
        return conditions;
    }

    // Рекурсивное извлечение условий корреляции из узла
    private void extractCorrelationsFromNode(Node node, List<CorrelationCondition> conditions) {

        if (node == null) {
            return;
        }

        // Проверяем логические условия на равенство внешних и внутренних полей
        if (node.getNodeType() == NodeType.LOGICAL_CHECK) {
            processLogicalCheckForCorrelations(node, conditions);
        }

        if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                extractCorrelationsFromNode(child, conditions);
            }
        }
    }

    // Обработка логической проверки для извлечения условий корреляции
    private void processLogicalCheckForCorrelations(Node logicalCheckNode,
                                                    List<CorrelationCondition> conditions) {

        if (logicalCheckNode.getChildren() == null) {
            return;
        }

        String operator = null;
        List<String> leftOperands = new ArrayList<>();
        List<String> rightOperands = new ArrayList<>();
        boolean processingLeft = true;

        for (Node child : logicalCheckNode.getChildren()) {

            if (child.getNodeType() == NodeType.TERMINAL) {

                Token token = child.getToken();

                if (token.category == Category.LOGICAL_OPERATOR) {

                    operator = token.lexeme;
                    processingLeft = false; // После оператора начинаются правые операнды

                } else if (token.lexeme.equals("=") ||
                        token.lexeme.equals("!=") ||
                        token.lexeme.equals("<") ||
                        token.lexeme.equals(">") ||
                        token.lexeme.equals("<=") ||
                        token.lexeme.equals(">=")) {
                    operator = token.lexeme;
                    processingLeft = false;
                }
            } else if (child.getNodeType() == NodeType.IDENTIFIER) {

                String identifier = buildIdentifierString(child);

                if (processingLeft) {
                    leftOperands.add(identifier);
                } else {
                    rightOperands.add(identifier);
                }
            }
        }

        // Если нашли оператор сравнения и операнды с обеих сторон
        if (operator != null && !leftOperands.isEmpty() && !rightOperands.isEmpty()) {
            // Проверяем, является ли это условием корреляции
            for (String left : leftOperands) {

                for (String right : rightOperands) {

                    if (isCorrelationCondition(left, right)) {
                        CorrelationCondition correlation = new CorrelationCondition(left, right, operator);
                        conditions.add(correlation);
                    }
                }
            }
        }
    }

    //Проверка, является ли пара идентификаторов условием корреляции
    private boolean isCorrelationCondition(String left, String right) {
        // Условие корреляции, если один идентификатор ссылается на внешнюю таблицу,
        // а другой - на внутреннюю (или наоборот)

        boolean leftIsExternal = isExternalReference(left);
        boolean rightIsExternal = isExternalReference(right);

        // Должен быть ровно один внешний и один внутренний идентификатор
        return leftIsExternal != rightIsExternal;
    }

    //Проверка, является ли идентификатор ссылкой на внешнюю таблицу
    private boolean isExternalReference(String identifier) {

        if (identifier == null) {
            return false;
        }

        String tablePart = extractTableFromIdentifier(identifier);

        return outerTables.contains(tablePart) || outerAliases.containsKey(tablePart);
    }

    // Извлечение табличной части из строки идентификатора
    private String extractTableFromIdentifier(String identifier) {

        if (!identifier.contains(".")) {
            return identifier; // Без квалификации - вероятно, алиас
        }

        String[] parts = identifier.split("\\.");
        if (parts.length >= 2) {
            return parts[0]; // table.column
        }

        return identifier;
    }

    // Построение строки идентификатора из узла
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
            sb.deleteCharAt(sb.length() - 1); // Удаляем последнюю точку
        }

        return sb.toString();
    }
}
