package sql.to.mongodb.translator.service.code.generator;

import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.*;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;

import java.util.*;

/**
 * Генератор MongoDB кода из промежуточного представления
 * Поддерживает два режима: find() и aggregate()
 */
public class CodeGenerator {

    private final SqlToMongoIR ir;
    private final StringBuilder output = new StringBuilder();
    private int indentLevel = 0;

    public CodeGenerator(SqlToMongoIR ir) {
        this.ir = ir;
    }

    public String generate() throws CodeGenerationException {
        output.setLength(0);

        if (ir == null) {
            throw new CodeGenerationException("IR cannot be null");
        }

        if (ir.getMainCollection() == null) {
            throw new CodeGenerationException("Main collection not specified");
        }

        if (ir.isRequiresAggregation() || ir.isHasJoins() || ir.isHasSubqueries() ||
                ir.isHasGroupBy() || ir.isHasComplexProjections()) {
            generateAggregationPipeline();
        } else {
            generateFindQuery();
        }

        return output.toString();
    }

    private void generateFindQuery() {
        // db.collection.find(query, projection).sort().limit().skip()
        String collection = escapeIdentifier(ir.getMainCollection());

        output.append("db.").append(collection).append(".find(");

        // Query part
        String query = generateQueryDocument();
        output.append(query.isEmpty() ? "{}" : query);

        // Projection part
        String projection = generateProjectionDocument();
        if (!projection.isEmpty()) {
            output.append(", ").append(projection);
        }
        output.append(")");

        // Sort
        if (!ir.getOrderBy().isEmpty()) {
            output.append(".sort(").append(generateSortDocument()).append(")");
        }

        // Limit/Skip
        if (ir.getLimit() != null) {
            output.append(".limit(").append(ir.getLimit()).append(")");
        }
        if (ir.getOffset() != null) {
            output.append(".skip(").append(ir.getOffset()).append(")");
        }

        output.append(";");
    }

    private void generateAggregationPipeline() {
        String collection = escapeIdentifier(ir.getMainCollection());
        output.append("db.").append(collection).append(".aggregate([\n");
        indentLevel++;

        List<String> stages = new ArrayList<>();

        // $match stage (WHERE conditions)
        String matchStage = generateMatchStage();
        if (!matchStage.isEmpty()) {
            stages.add(matchStage);
        }

        // $lookup stages (JOINs)
        stages.addAll(generateLookupStages());

        // $match stage for joined collections (JOIN conditions)
        String joinMatchStage = generateJoinMatchStage();
        if (!joinMatchStage.isEmpty()) {
            stages.add(joinMatchStage);
        }

        // $group stage (GROUP BY + aggregations)
        String groupStage = generateGroupStage();
        if (!groupStage.isEmpty()) {
            stages.add(groupStage);
        }

        // $match stage (HAVING)
        String havingStage = generateHavingStage();
        if (!havingStage.isEmpty()) {
            stages.add(havingStage);
        }

        // $sort stage
        String sortStage = generateSortStage();
        if (!sortStage.isEmpty()) {
            stages.add(sortStage);
        }

        // $skip/$limit stages
        if (ir.getOffset() != null) {
            stages.add(indent() + "{ $skip: " + ir.getOffset() + " }");
        }
        if (ir.getLimit() != null) {
            stages.add(indent() + "{ $limit: " + ir.getLimit() + " }");
        }

        // $project stage (final projection)
        String projectStage = generateProjectStage();
        if (!projectStage.isEmpty()) {
            stages.add(projectStage);
        }

        output.append(String.join(",\n", stages));
        output.append("\n"); indentLevel--;
        output.append("]);");
    }

    // ========== Генерация отдельных стадий ==========

    private String generateMatchStage() {
        if (ir.getWhereConditions().isEmpty()) {
            return "";
        }

        ConditionNode root = combineConditions(ir.getWhereConditions());
        String condition = translateCondition(root);

        return indent() + "{ $match: " + condition + " }";
    }

    private List<String> generateLookupStages() {
        List<String> stages = new ArrayList<>();

        for (JoinInfo join : ir.getJoins()) {
            String from = escapeIdentifier(join.getRightTable());
            String as = join.getRightAlias() != null ?
                    escapeIdentifier(join.getRightAlias()) :
                    escapeIdentifier(join.getRightTable());

            // Определяем поля для связи
            ConditionNode joinCondition = join.getJoinCondition();
            String localField = "???";
            String foreignField = "???";

            if (joinCondition != null) {
                // Извлекаем поля из условия JOIN
                String[] fields = extractJoinFields(joinCondition);
                localField = fields[0];
                foreignField = fields[1];
            }

            String lookup = indent() + "{ $lookup: {\n";
            indentLevel++;
            lookup += indent() + "from: \"" + from + "\",\n";
            lookup += indent() + "localField: \"" + localField + "\",\n";
            lookup += indent() + "foreignField: \"" + foreignField + "\",\n";
            lookup += indent() + "as: \"" + as + "\"\n";
            indentLevel--;
            lookup += indent() + "} }";

            stages.add(lookup);

            // Разворачиваем массив для LEFT JOIN
            if (join.getType() == JoinInfo.JoinType.LEFT) {
                String unwind = indent() + "{ $unwind: {\n";
                indentLevel++;
                unwind += indent() + "path: \"$" + as + "\",\n";
                unwind += indent() + "preserveNullAndEmptyArrays: true\n";
                indentLevel--;
                unwind += indent() + "} }";
                stages.add(unwind);
            } else {
                String unwind = indent() + "{ $unwind: \"$" + as + "\" }";
                stages.add(unwind);
            }
        }

        return stages;
    }

    private String generateJoinMatchStage() {
        // Объединяем все условия JOIN
        List<ConditionNode> joinConditions = new ArrayList<>();
        for (JoinInfo join : ir.getJoins()) {
            if (join.getJoinCondition() != null) {
                joinConditions.add(join.getJoinCondition());
            }
        }

        if (joinConditions.isEmpty()) {
            return "";
        }

        ConditionNode root = combineConditions(joinConditions);
        String condition = translateCondition(root, true);

        return indent() + "{ $match: " + condition + " }";
    }

    private String generateGroupStage() {
        if (!ir.isHasGroupBy() && !ir.isHasAggregateFunctions()) {
            return "";
        }

        StringBuilder group = new StringBuilder(indent() + "{ $group: {\n");
        indentLevel++;

        // _id
        group.append(indent()).append("_id: ");
        if (ir.getGroupByFields().isEmpty()) {
            group.append("null");
        } else if (ir.getGroupByFields().size() == 1) {
            group.append("\"$").append(ir.getGroupByFields().getFirst()).append("\"");
        } else {
            group.append("{\n");
            indentLevel++;
            for (int i = 0; i < ir.getGroupByFields().size(); i++) {
                String field = ir.getGroupByFields().get(i);
                group.append(indent()).append(field).append(": \"$").append(field).append("\"");
                if (i < ir.getGroupByFields().size() - 1) {
                    group.append(",\n");
                }
            }
            indentLevel--;
            group.append("\n").append(indent()).append("}");
        }

        // Агрегации из проекций
        Map<String, String> aggregations = extractAggregations();
        if (!aggregations.isEmpty()) {
            group.append(",\n");
            List<String> aggParts = new ArrayList<>();
            for (Map.Entry<String, String> entry : aggregations.entrySet()) {
                aggParts.add(indent() + entry.getKey() + ": " + entry.getValue());
            }
            group.append(String.join(",\n", aggParts));
        }

        indentLevel--;
        group.append("\n").append(indent()).append("} }");

        return group.toString();
    }

    private String generateHavingStage() {
        if (ir.getHavingConditions().isEmpty()) {
            return "";
        }

        ConditionNode root = combineConditions(ir.getHavingConditions());
        String condition = translateCondition(root, true);

        return indent() + "{ $match: " + condition + " }";
    }

    private String generateSortStage() {
        if (ir.getOrderBy().isEmpty()) {
            return "";
        }

        StringBuilder sort = new StringBuilder(indent() + "{ $sort: { ");

        List<String> fields = new ArrayList<>();
        for (SortField sf : ir.getOrderBy()) {
            String field = sf.getFullField();
            int direction = sf.getDirection() == SortField.SortDirection.ASC ? 1 : -1;
            fields.add(field + ": " + direction);
        }

        sort.append(String.join(", ", fields));
        sort.append(" } }");

        return sort.toString();
    }

    private String generateProjectStage() {
        if (ir.getProjectionFields().isEmpty()) {
            return "";
        }

        boolean includeAll = ir.getProjectionFields().stream()
                .anyMatch(ProjectionField::isAllFields);

        if (includeAll) {
            return "";
        }

        StringBuilder project = new StringBuilder(indent() + "{ $project: {\n");
        indentLevel++;

        // Исключаем _id по умолчанию, если не указан
        boolean hasId = ir.getProjectionFields().stream()
                .anyMatch(f -> "_id".equals(f.getField()) || "_id".equals(f.getAlias()));

        if (!hasId) {
            project.append(indent()).append("_id: 0,\n");
        }

        List<String> fields = new ArrayList<>();
        for (ProjectionField pf : ir.getProjectionFields()) {
            String name = pf.getAlias() != null ? pf.getAlias() : pf.getField();
            String value;

            if (pf.getField().contains("(")) { // Агрегатная функция
                value = translateAggregateExpression(pf.getField());
            } else if (pf.getField().equals("*")) {
                continue;
            } else {
                String source = pf.getSource() != null ? pf.getSource() + "." : "";
                value = "\"$" + source + pf.getField() + "\"";
            }

            fields.add(indent() + name + ": " + value);
        }

        project.append(String.join(",\n", fields));
        indentLevel--;
        project.append("\n").append(indent()).append("} }");

        return project.toString();
    }

    private String generateProjectionDocument() {
        if (ir.getProjectionFields().isEmpty()) {
            return "";
        }

        boolean includeAll = ir.getProjectionFields().stream()
                .anyMatch(ProjectionField::isAllFields);

        if (includeAll) {
            return "{}";
        }

        StringBuilder proj = new StringBuilder("{ ");

        // Исключаем _id по умолчанию
        boolean hasId = ir.getProjectionFields().stream()
                .anyMatch(f -> "_id".equals(f.getField()) || "_id".equals(f.getAlias()));

        if (!hasId) {
            proj.append("_id: 0, ");
        }

        List<String> fields = new ArrayList<>();
        for (ProjectionField pf : ir.getProjectionFields()) {
            String name = pf.getAlias() != null ? pf.getAlias() : pf.getField();
            if (!pf.getField().equals("*")) {
                fields.add(name + ": 1");
            }
        }

        proj.append(String.join(", ", fields));
        proj.append(" }");

        return proj.toString();
    }

    private String generateQueryDocument() {
        if (ir.getWhereConditions().isEmpty()) {
            return "";
        }

        ConditionNode root = combineConditions(ir.getWhereConditions());
        return translateCondition(root);
    }

    private String generateSortDocument() {
        if (ir.getOrderBy().isEmpty()) {
            return "";
        }

        StringBuilder sort = new StringBuilder("{ ");
        List<String> fields = new ArrayList<>();
        for (SortField sf : ir.getOrderBy()) {
            String field = sf.getFullField();
            int direction = sf.getDirection() == SortField.SortDirection.ASC ? 1 : -1;
            fields.add(field + ": " + direction);
        }
        sort.append(String.join(", ", fields));
        sort.append(" }");

        return sort.toString();
    }

    // ========== Трансляция условий ==========

    private String translateCondition(ConditionNode node) {
        return translateCondition(node, false);
    }

    private String translateCondition(ConditionNode node, boolean useAggregationSyntax) {
        if (node == null) return "{}";

        return switch (node.getType()) {
            case AND, OR -> translateLogicalCondition(node, useAggregationSyntax);
            case COMPARISON -> translateComparisonCondition(node, useAggregationSyntax);
            case IS_NULL -> translateIsNullCondition(node, true, useAggregationSyntax);
            case IS_NOT_NULL -> translateIsNullCondition(node, false, useAggregationSyntax);
            case BETWEEN -> translateBetweenCondition(node, useAggregationSyntax);
            case IN -> translateInCondition(node, useAggregationSyntax);
            case EXISTS, NOT_EXISTS -> translateExistsCondition(node, useAggregationSyntax);
            default -> "{}";
        };
    }

    private String translateLogicalCondition(ConditionNode node, boolean useAggregationSyntax) {
        List<String> parts = new ArrayList<>();
        for (ConditionNode child : node.getChildren()) {
            String childCond = translateCondition(child, useAggregationSyntax);
            if (!childCond.isEmpty() && !childCond.equals("{}")) {
                parts.add(childCond);
            }
        }

        if (parts.isEmpty()) return "{}";
        if (parts.size() == 1) return parts.getFirst();

        String op = node.getType() == ConditionNode.ConditionType.AND ? "$and" : "$or";
        return "{ " + op + ": [ " + String.join(", ", parts) + " ] }";
    }

    private String translateComparisonCondition(ConditionNode node, boolean useAggregationSyntax) {
        String field = node.getField();
        Object value = node.getValue();
        String operator = node.getOperator() != null ? node.getOperator() : "=";

        // Экранируем поле
        field = escapeField(field, useAggregationSyntax);

        // Преобразуем значение
        String valueStr = formatValue(value, useAggregationSyntax);

        // Маппинг операторов
        String mongoOp = mapOperator(operator);

        if (mongoOp.equals("$eq") && !useAggregationSyntax) {
            return "{ " + field + ": " + valueStr + " }";
        } else {
            return "{ " + field + ": { " + mongoOp + ": " + valueStr + " } }";
        }
    }

    private String translateIsNullCondition(ConditionNode node, boolean isNull, boolean useAggregationSyntax) {
        String field = escapeField(node.getField(), useAggregationSyntax);

        if (useAggregationSyntax) {
            return "{ $expr: { " + (isNull ? "$eq" : "$ne") + ": [ \"$" + field + "\", null ] } }";
        } else {
            return "{ " + field + ": " + (isNull ? "null" : "{ $ne: null }") + " }";
        }
    }

    private String translateBetweenCondition(ConditionNode node, boolean useAggregationSyntax) {
        String field = escapeField(node.getField(), useAggregationSyntax);
        List<?> values = (List<?>) node.getValue();

        if (values == null || values.size() < 2) {
            return "{}";
        }

        String from = formatValue(values.get(0), useAggregationSyntax);
        String to = formatValue(values.get(1), useAggregationSyntax);

        return "{ " + field + ": { $gte: " + from + ", $lte: " + to + " } }";
    }

    private String translateInCondition(ConditionNode node, boolean useAggregationSyntax) {
        String field = escapeField(node.getField(), useAggregationSyntax);
        Object value = node.getValue();

        if (value instanceof List<?> list) {
            List<String> formatted = new ArrayList<>();
            for (Object item : list) {
                formatted.add(formatValue(item, useAggregationSyntax));
            }
            return "{ " + field + ": { $in: [ " + String.join(", ", formatted) + " ] } }";
        } else if (value instanceof SubqueryInfo) {
            // Для подзапросов нужно специальная обработка
            return "{ " + field + ": { $in: ... } }"; // TODO: обработка подзапросов
        }

        return "{}";
    }

    private String translateExistsCondition(ConditionNode node, boolean useAggregationSyntax) {
        String field = escapeField(node.getField(), useAggregationSyntax);
        boolean exists = node.getType() == ConditionNode.ConditionType.EXISTS;

        if (useAggregationSyntax) {
            return "{ $expr: { " + (exists ? "$ne" : "$eq") + ": [ { $type: \"$" + field + "\" }, \"missing\" ] } }";
        } else {
            return "{ " + field + ": { $exists: " + exists + " } }";
        }
    }

    // ========== Вспомогательные методы ==========

    private ConditionNode combineConditions(List<ConditionNode> conditions) {
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.getFirst();

        ConditionNode root = new ConditionNode();
        root.setType(ConditionNode.ConditionType.AND);
        root.getChildren().addAll(conditions);
        return root;
    }

    private String[] extractJoinFields(ConditionNode condition) {
        // Упрощенная версия: ожидаем условие вида table1.field = table2.field
        String field1 = condition.getField();
        Object value = condition.getValue();

        if (value instanceof String field2) {

            // Определяем, какое поле из какой таблицы
            if (field1.contains(".")) {
                String[] parts1 = field1.split("\\.");
                String[] parts2 = field2.split("\\.");

                // Предполагаем, что первое поле из левой таблицы, второе из правой
                return new String[] { parts1[1], parts2[1] };
            }
        }

        return new String[] { "id", "id" }; // Значения по умолчанию
    }

    private Map<String, String> extractAggregations() {
        Map<String, String> result = new LinkedHashMap<>();

        for (ProjectionField pf : ir.getProjectionFields()) {
            String field = pf.getField();
            if (field.contains("(")) {
                String alias = pf.getAlias() != null ? pf.getAlias() : field;
                String aggExpr = translateAggregateExpression(field);
                result.put(alias, aggExpr);
            }
        }

        return result;
    }

    private String translateAggregateExpression(String expr) {
        expr = expr.trim();

        if (expr.startsWith("COUNT(")) {
            String field = expr.substring(6, expr.length() - 1);
            return "{ $sum: 1 }";
        } else if (expr.startsWith("SUM(")) {
            String field = expr.substring(4, expr.length() - 1);
            return "{ $sum: \"$" + field + "\" }";
        } else if (expr.startsWith("AVG(")) {
            String field = expr.substring(4, expr.length() - 1);
            return "{ $avg: \"$" + field + "\" }";
        } else if (expr.startsWith("MIN(")) {
            String field = expr.substring(4, expr.length() - 1);
            return "{ $min: \"$" + field + "\" }";
        } else if (expr.startsWith("MAX(")) {
            String field = expr.substring(4, expr.length() - 1);
            return "{ $max: \"$" + field + "\" }";
        }

        return "{ $first: \"$$ROOT\" }";
    }

    private String mapOperator(String sqlOp) {
        return switch (sqlOp) {
            case "!=", "<>" -> "$ne";
            case "<" -> "$lt";
            case "<=" -> "$lte";
            case ">" -> "$gt";
            case ">=" -> "$gte";
            case "LIKE" -> "$regex";
            default -> "$eq";
        };
    }

    private String formatValue(Object value, boolean useAggregationSyntax) {
        if (value == null) return "null";

        if (value instanceof String str) {
            if (str.startsWith("'") && str.endsWith("'")) {
                return "\"" + str.substring(1, str.length() - 1) + "\"";
            } else if (str.contains(".")) {
                if (useAggregationSyntax) {
                    return "\"$" + str + "\"";
                } else {
                    return str; // Поле в find()
                }
            }
            return "\"" + str + "\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof SubqueryInfo) {
            return "/* subquery */";
        }

        return String.valueOf(value);
    }

    private String escapeField(String field, boolean useAggregationSyntax) {
        if (field == null) return "";

        // Убираем кавычки, если есть
        if (field.startsWith("'") && field.endsWith("'")) {
            field = field.substring(1, field.length() - 1);
        }

        if (useAggregationSyntax) {
            return field.replace(".", "__"); // Для агрегации экранируем точки
        }

        return field;
    }

    private String escapeIdentifier(String identifier) {
        return identifier;
    }

    private String indent() {
        return "  ".repeat(indentLevel);
    }
}
