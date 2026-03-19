package sql.to.mongodb.translator.service.code.generator.stages;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.expressions.AggregateExpressionBuilder;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GroupStageGenerator extends BaseGenerator {

    private final AggregateExpressionBuilder aggBuilder;

    public GroupStageGenerator(AggregateExpressionBuilder aggBuilder) {
        this.aggBuilder = aggBuilder;
    }

    @Override
    public String generate(SqlToMongoIR ir, GenerationContext context) {
        this.ir = ir;
        this.context = context;

        if (!ir.isHasGroupBy() && !ir.isHasAggregateFunctions()) {
            return "";
        }

        StringBuilder group = new StringBuilder(indent() + "{ $group: {\n");
        increaseIndent();

        // _id
        group.append(indent()).append("_id: ");
        generateGroupId(group);

        // Агрегации
        Map<String, String> aggregations = extractAggregations();
        if (!aggregations.isEmpty()) {
            group.append(",\n");
            List<String> aggParts = new ArrayList<>();
            for (Map.Entry<String, String> entry : aggregations.entrySet()) {
                aggParts.add(indent() + entry.getKey() + ": " + entry.getValue());
            }
            group.append(String.join(",\n", aggParts));
        }

        decreaseIndent();
        group.append("\n").append(indent()).append("} }");

        return group.toString();
    }

    private void generateGroupId(StringBuilder group) {
        if (ir.getGroupByFields().isEmpty()) {
            group.append("null");
        } else if (ir.getGroupByFields().size() == 1) {
            String field = ir.getGroupByFields().getFirst();
            group.append("\"$").append(field).append("\"");
        } else {
            group.append("{\n");
            increaseIndent();
            for (int i = 0; i < ir.getGroupByFields().size(); i++) {
                String field = ir.getGroupByFields().get(i);
                group.append(indent()).append(field).append(": \"$").append(field).append("\"");
                if (i < ir.getGroupByFields().size() - 1) {
                    group.append(",\n");
                }
            }
            decreaseIndent();
            group.append("\n").append(indent()).append("}");
        }
    }

    private Map<String, String> extractAggregations() {
        Map<String, String> result = new LinkedHashMap<>();

        for (ProjectionField pf : ir.getProjectionFields()) {
            if (pf.getField().contains("(")) {
                String alias = pf.getAlias() != null ? pf.getAlias() : pf.getField();

                // Парсим агрегатную функцию
                AggregateFunctionInfo aggInfo = parseAggregateFunction(pf.getField());
                String aggExpr = aggBuilder.translate(aggInfo.functionName, aggInfo.argument, aggInfo.distinct);

                result.put(alias, aggExpr);
            }
        }

        return result;
    }

    /**
     * Парсит агрегатную функцию из строки
     * Например: "COUNT(*)" -> functionName="COUNT", argument="*", distinct=false
     * "SUM(DISTINCT salary)" -> functionName="SUM", argument="salary", distinct=true
     */
    private AggregateFunctionInfo parseAggregateFunction(String fieldExpr) {
        String functionName = "";
        String argument = "";
        boolean distinct = false;

        int openParen = fieldExpr.indexOf('(');
        int closeParen = fieldExpr.lastIndexOf(')');

        if (openParen > 0 && closeParen > openParen) {
            functionName = fieldExpr.substring(0, openParen).trim().toUpperCase();
            String args = fieldExpr.substring(openParen + 1, closeParen).trim();

            // Проверяем на DISTINCT
            if (args.toUpperCase().startsWith("DISTINCT ")) {
                distinct = true;
                argument = args.substring(9).trim(); // убираем "DISTINCT "
            } else {
                argument = args;
            }
        }

        return new AggregateFunctionInfo(functionName, argument, distinct);
    }

    private record AggregateFunctionInfo(String functionName, String argument, boolean distinct) {}
}
