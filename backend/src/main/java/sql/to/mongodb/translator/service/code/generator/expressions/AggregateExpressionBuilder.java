package sql.to.mongodb.translator.service.code.generator.expressions;

import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.enums.NodeType;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.parser.Node;

import java.util.HashMap;
import java.util.Map;

public class AggregateExpressionBuilder {

    private final ExpressionBuilder exprBuilder;

    private static final Map<String, String> AGGREGATE_MAPPING = new HashMap<>();

    static {
        AGGREGATE_MAPPING.put("COUNT", "$sum");
        AGGREGATE_MAPPING.put("SUM", "$sum");
        AGGREGATE_MAPPING.put("AVG", "$avg");
        AGGREGATE_MAPPING.put("MIN", "$min");
        AGGREGATE_MAPPING.put("MAX", "$max");
        AGGREGATE_MAPPING.put("FIRST", "$first");
        AGGREGATE_MAPPING.put("LAST", "$last");
        AGGREGATE_MAPPING.put("STDDEV", "$stdDevPop");
        AGGREGATE_MAPPING.put("STDDEV_SAMP", "$stdDevSamp");
        AGGREGATE_MAPPING.put("VARIANCE", "$variancePop");
        AGGREGATE_MAPPING.put("VAR_SAMP", "$varianceSamp");
    }

    public AggregateExpressionBuilder(SqlToMongoIR ir, GenerationContext context) {
        this.exprBuilder = new ExpressionBuilder(ir, context);
    }

    /**
     * Построение агрегатного выражения из узла AST
     */
    public String build(Node node) {
        if (node == null || node.getChildren() == null) return "";

        String functionName = "";
        String argument = "";
        boolean distinct = false;

        for (Node child : node.getChildren()) {
            if (child.getNodeType() == NodeType.TERMINAL) {
                String lexeme = child.getToken().lexeme;
                if (isAggregateFunction(lexeme)) {
                    functionName = lexeme;
                } else if ("DISTINCT".equals(lexeme)) {
                    distinct = true;
                }
            } else {
                argument = exprBuilder.build(child);
            }
        }

        return translate(functionName, argument, distinct);
    }

    /**
     * Трансляция агрегатной функции в MongoDB оператор
     */
    public String translate(String sqlFunction, String argument, boolean distinct) {
        String mongoFunction = AGGREGATE_MAPPING.get(sqlFunction.toUpperCase());

        if (mongoFunction == null) {
            return "{ $first: \"$$ROOT\" }"; // По умолчанию
        }

        // Обработка DISTINCT
        if (distinct && "$sum".equals(mongoFunction)) {
            // COUNT(DISTINCT field) требует специальной обработки
            return "{ $sum: { $cond: [ { $eq: [ \"$$ROOT\", \"$$ROOT\" ] }, 1, 0 ] } }";
        }

        // Для COUNT(*) специальная обработка
        if ("$sum".equals(mongoFunction) && (argument.isEmpty() || "*".equals(argument))) {
            return "{ $sum: 1 }";
        }

        // Для остальных функций
        if (argument.isEmpty()) {
            return "{ " + mongoFunction + ": 1 }";
        } else {
            return "{ " + mongoFunction + ": " + argument + " }";
        }
    }

    /**
     * Трансляция агрегатной функции для использования в $group
     */
    public String translateForGroup(String sqlFunction, String field) {
        String mongoFunction = AGGREGATE_MAPPING.get(sqlFunction.toUpperCase());

        if (mongoFunction == null) {
            return "$first";
        }

        return mongoFunction;
    }

    /**
     * Трансляция агрегатной функции для использования в $project
     */
    public String translateForProject(String sqlFunction, String field) {
        String mongoFunction = AGGREGATE_MAPPING.get(sqlFunction.toUpperCase());

        if (mongoFunction == null) {
            return "$" + field;
        }

        if (field.isEmpty() || "*".equals(field)) {
            return "{ " + mongoFunction + ": 1 }";
        }

        return "{ " + mongoFunction + ": \"$" + field + "\" }";
    }

    /**
     * Проверка, является ли токен агрегатной функцией
     */
    private boolean isAggregateFunction(String token) {
        return AGGREGATE_MAPPING.containsKey(token.toUpperCase());
    }

    /**
     * Получение списка поддерживаемых агрегатных функций
     */
    public static String[] getSupportedFunctions() {
        return AGGREGATE_MAPPING.keySet().toArray(new String[0]);
    }
}
