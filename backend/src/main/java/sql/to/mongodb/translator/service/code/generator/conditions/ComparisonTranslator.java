package sql.to.mongodb.translator.service.code.generator.conditions;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.helpers.FormatHelper;
import sql.to.mongodb.translator.service.code.generator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.service.code.generator.stages.SubqueryStageGenerator;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Транслятор условий сравнения в MongoDB синтаксис
 * Поддерживает все типы сравнений и подзапросы
 */
@Component
public class ComparisonTranslator {

    private final SubqueryStageGenerator subqueryGenerator;

    public ComparisonTranslator(SubqueryStageGenerator subqueryGenerator1) {
        this.subqueryGenerator = subqueryGenerator1;
    }

    /**
     * Трансляция условия сравнения
     */
    public String translate(ConditionNode node,
                            GenerationContext context) throws CodeGenerationException {
        String field = node.getField();
        Object value = node.getValue();
        String operator = node.getOperator() != null ? node.getOperator() : "=";

        field = FormatHelper.escapeField(field, context);

        // Обработка подзапросов
        if (value instanceof SubqueryInfo subquery) {
            return translateWithSubquery(field, subquery, operator, context);
        }

        // Обработка списка значений (для IN)
        if (value instanceof List<?> list) {
            return translateListComparison(field, list, operator, context);
        }

        // Обычное сравнение с константой
        String valueStr = FormatHelper.formatValue(value, context);
        return translateSimpleComparison(field, valueStr, operator, context);
    }

    /**
     * Трансляция простого сравнения с константой
     */
    private String translateSimpleComparison(String field,
                                             String valueStr,
                                             String operator,
                                             GenerationContext context) {
        String mongoOp = mapOperator(operator);

        // Специальная обработка для LIKE
        if ("LIKE".equalsIgnoreCase(operator)) {
            return translateLike(field, valueStr, context);
        }

        // Для aggregation syntax
        if (context.isUseAggregationSyntax()) {
            return translateAggregationComparison(field, valueStr, mongoOp);
        }

        // Для find() syntax
        return translateFindComparison(field, valueStr, mongoOp);
    }

    /**
     * Трансляция сравнения для aggregation pipeline
     */
    private String translateAggregationComparison(String field, String valueStr, String mongoOp) {
        if ("$eq".equals(mongoOp)) {
            return "{ $expr: { $eq: [ \"$" + field + "\", " + valueStr + " ] } }";
        } else {
            return "{ $expr: { " + mongoOp + ": [ \"$" + field + "\", " + valueStr + " ] } }";
        }
    }

    /**
     * Трансляция сравнения для find() запроса
     */
    private String translateFindComparison(String field, String valueStr, String mongoOp) {
        if ("$eq".equals(mongoOp)) {
            return "{ \"" + field + "\": " + valueStr + " }";
        } else {
            return "{ \"" + field + "\": { " + mongoOp + ": " + valueStr + " } }";
        }
    }

    /**
     * Трансляция условия LIKE
     */
    private String translateLike(String field, String pattern, GenerationContext context) {
        // Убираем кавычки из паттерна
        String cleanPattern = pattern.replace("'", "").replace("\"", "");

        // Преобразуем SQL LIKE в regex
        String regex = cleanPattern
                .replace("%", ".*")
                .replace("_", ".");

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $regexMatch: { input: \"$" + field + "\", regex: \"" + regex + "\" } } }";
        } else {
            return "{ \"" + field + "\": { $regex: \"" + regex + "\" } }";
        }
    }

    /**
     * Трансляция сравнения со списком значений
     */
    private String translateListComparison(String field,
                                           List<?> values,
                                           String operator,
                                           GenerationContext context) throws CodeGenerationException {
        List<String> formatted = new ArrayList<>();

        for (Object item : values) {
            if (item instanceof SubqueryInfo subquery) {
                // Для подзапросов в списке
                String subqueryResult = generateSubqueryReference(subquery, context);
                formatted.add(subqueryResult);
            } else {
                formatted.add(FormatHelper.formatValue(item, context));
            }
        }

        if ("IN".equalsIgnoreCase(operator)) {
            return translateIn(field, formatted, context);
        } else if ("NOT IN".equalsIgnoreCase(operator)) {
            return translateNotIn(field, formatted, context);
        } else {
            // Для других операторов со списком (например, = ANY)
            return translateAnyComparison(field, formatted, operator, context);
        }
    }

    /**
     * Трансляция условия IS NULL / IS NOT NULL
     */
    public String translateIsNull(ConditionNode node, GenerationContext context) {
        String field = FormatHelper.escapeField(node.getField(), context);
        boolean isNull = node.getType() == ConditionNode.ConditionType.IS_NULL;

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { " + (isNull ? "$eq" : "$ne") +
                    ": [ \"$" + field + "\", null ] } }";
        } else {
            return "{ \"" + field + "\": " + (isNull ? "null" : "{ $ne: null }") + " }";
        }
    }

    /**
     * Трансляция условия BETWEEN
     */
    public String translateBetween(ConditionNode node, GenerationContext context) {
        String field = FormatHelper.escapeField(node.getField(), context);
        List<?> values = (List<?>) node.getValue();

        if (values == null || values.size() < 2) {
            return "{}";
        }

        String from = FormatHelper.formatValue(values.get(0), context);
        String to = FormatHelper.formatValue(values.get(1), context);

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $and: [ " +
                    "{ $gte: [ \"$" + field + "\", " + from + " ] }, " +
                    "{ $lte: [ \"$" + field + "\", " + to + " ] } ] } }";
        } else {
            return "{ \"" + field + "\": { $gte: " + from + ", $lte: " + to + " } }";
        }
    }

    /**
     * Трансляция условия IN
     */
    public String translateIn(ConditionNode node, GenerationContext context) throws CodeGenerationException {
        String field = FormatHelper.escapeField(node.getField(), context);
        Object value = node.getValue();

        if (value instanceof SubqueryInfo subquery) {
            return translateInWithSubquery(field, subquery, context);
        } else if (value instanceof List<?> list) {
            List<String> formatted = formatValueList(list, context);
            return translateIn(field, formatted, context);
        }

        return "{}";
    }

    /**
     * Трансляция условия IN со списком значений
     */
    private String translateIn(String field, List<String> values, GenerationContext context) {
        if (values.isEmpty()) {
            return "{}";
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $in: [ \"$" + field + "\", [ " +
                    String.join(", ", values) + " ] ] } }";
        } else {
            return "{ \"" + field + "\": { $in: [ " +
                    String.join(", ", values) + " ] } }";
        }
    }

    /**
     * Трансляция условия NOT IN
     */
    private String translateNotIn(String field, List<String> values, GenerationContext context) {
        if (values.isEmpty()) {
            return "{}";
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $not: { $in: [ \"$" + field + "\", [ " +
                    String.join(", ", values) + " ] ] } } }";
        } else {
            return "{ \"" + field + "\": { $nin: [ " +
                    String.join(", ", values) + " ] } }";
        }
    }

    /**
     * Трансляция условия ANY/SOME
     */
    private String translateAnyComparison(String field,
                                          List<String> values,
                                          String operator,
                                          GenerationContext context) {
        String mongoOp = mapOperator(operator);
        List<String> conditions = new ArrayList<>();

        for (String value : values) {
            if (context.isUseAggregationSyntax()) {
                conditions.add("{ $expr: { " + mongoOp + ": [ \"$" + field + "\", " + value + " ] } }");
            } else {
                conditions.add("{ \"" + field + "\": { " + mongoOp + ": " + value + " } }");
            }
        }

        return "{ $or: [ " + String.join(", ", conditions) + " ] }";
    }

    /**
     * Трансляция условия EXISTS / NOT EXISTS
     */
    public String translateExists(ConditionNode node, GenerationContext context) throws CodeGenerationException {
        Object value = node.getValue();

        if (value instanceof SubqueryInfo subquery) {
            boolean exists = node.getType() == ConditionNode.ConditionType.EXISTS;
            return translateExistsWithSubquery(subquery, exists, context);
        }

        return translateIsNull(node, context);
    }

    /**
     * Трансляция EXISTS с подзапросом
     */
    private String translateExistsWithSubquery(SubqueryInfo subquery,
                                               boolean exists,
                                               GenerationContext context) throws CodeGenerationException {

        String subqueryRef;

        if (SubqueryHelper.isCorrelated(subquery)) {
            // Для коррелированного подзапроса
            subqueryRef = generateCorrelatedSubqueryReference(subquery, context);
        } else {
            // Для некоррелированного подзапроса
            subqueryRef = generateSubqueryReference(subquery, context);
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { " + (exists ? "$gt" : "$eq") +
                    ": [ { $size: " + subqueryRef + " }, 0 ] } }";
        } else {
            return "{ $where: \"" + subqueryRef + ".length " +
                    (exists ? ">" : "==") + " 0\" }";
        }
    }

    /**
     * Трансляция сравнения с подзапросом
     */
    private String translateWithSubquery(String field,
                                         SubqueryInfo subquery,
                                         String operator,
                                         GenerationContext context)
            throws CodeGenerationException {

        String mongoOp = mapOperator(operator);
        String subqueryRef;

        if (SubqueryHelper.isCorrelated(subquery)) {
            subqueryRef = generateCorrelatedSubqueryReference(subquery, context);
        } else {
            subqueryRef = generateSubqueryReference(subquery, context);
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { " + mongoOp + ": [ \"$" + field + "\", " + subqueryRef + " ] } }";
        } else {
            return "{ \"" + field + "\": { " + mongoOp + ": " + subqueryRef + " } }";
        }
    }

    /**
     * Трансляция IN с подзапросом
     */
    private String translateInWithSubquery(String field,
                                           SubqueryInfo subquery,
                                           GenerationContext context)
            throws CodeGenerationException {

        String subqueryRef;

        if (SubqueryHelper.isCorrelated(subquery)) {
            subqueryRef = generateCorrelatedSubqueryReference(subquery, context);
        } else {
            subqueryRef = generateSubqueryReference(subquery, context);
        }

        if (context.isUseAggregationSyntax()) {
            return "{ $expr: { $in: [ \"$" + field + "\", " + subqueryRef + " ] } }";
        } else {
            return "{ \"" + field + "\": { $in: " + subqueryRef + " } }";
        }
    }

    /**
     * Генерация ссылки на результат подзапроса
     */
    private String generateSubqueryReference(SubqueryInfo subquery, GenerationContext context) throws CodeGenerationException {
        String resultName = context.nextSubqueryResult();
        context.getSubqueryResults().put(resultName, "subquery");

        SqlToMongoIR subIR = subquery.getSubqueryIR();
        String collection = FormatHelper.escapeIdentifier(subIR.getMainCollection());

        return "db." + collection + ".aggregate(" +
                subqueryGenerator.generateSubqueryPipeline(subquery) +
                ").toArray()";
    }

    /**
     * Генерация ссылки на результат коррелированного подзапроса
     */
    private String generateCorrelatedSubqueryReference(SubqueryInfo subquery, GenerationContext context) {
        String subqueryName = context.nextSubqueryName();

        List<CorrelationCondition> correlations = subquery.getCorrelations();
        String letVars = correlations.stream()
                .map(c -> {
                    String outerField = c.getOuterField().split("\\.")[1];
                    return outerField + ": \"$" + outerField + "\"";
                })
                .collect(Collectors.joining(", "));

        return "\"$" + subqueryName + "\"";
    }

    /**
     * Форматирование списка значений
     */
    private List<String> formatValueList(List<?> values, GenerationContext context) {
        List<String> formatted = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof String) {
                formatted.add(FormatHelper.formatValue(item, context));
            } else {
                formatted.add(String.valueOf(item));
            }
        }
        return formatted;
    }

    /**
     * Маппинг SQL операторов в MongoDB операторы
     */
    private String mapOperator(String sqlOp) {
        return switch (sqlOp) {
            case "!=", "<>" -> "$ne";
            case "<" -> "$lt";
            case "<=" -> "$lte";
            case ">" -> "$gt";
            case ">=" -> "$gte";
            case "LIKE" -> "$regex";
            case "IN" -> "$in";
            case "NOT IN" -> "$nin";
            default -> "$eq";
        };
    }
}