package sql.to.mongodb.translator.service.code.generator.stages;

import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.expressions.AggregateExpressionBuilder;
import sql.to.mongodb.translator.service.code.generator.helpers.FieldHelper;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;

import java.util.ArrayList;
import java.util.List;

public class ProjectStageGenerator extends BaseGenerator {

    private final AggregateExpressionBuilder aggBuilder;

    public ProjectStageGenerator(SqlToMongoIR ir, GenerationContext context) {
        super(ir, context);
        this.aggBuilder = new AggregateExpressionBuilder(ir, context);
    }

    @Override
    public String generate() throws CodeGenerationException {
        if (ir.getProjectionFields().isEmpty()) {
            return "";
        }

        boolean includeAll = ir.getProjectionFields().stream()
                .anyMatch(ProjectionField::isAllFields);

        if (includeAll) {
            return "";
        }

        StringBuilder project = new StringBuilder(indent() + "{ $project: {\n");
        increaseIndent();

        // Исключаем _id по умолчанию
        boolean hasId = ir.getProjectionFields().stream()
                .anyMatch(f -> "_id".equals(f.getField()) || "_id".equals(f.getAlias()));

        if (!hasId) {
            project.append(indent()).append("_id: 0,\n");
        }

        List<String> fields = new ArrayList<>();
        for (ProjectionField pf : ir.getProjectionFields()) {
            String name = FieldHelper.getDisplayName(pf);
            String value;

            if (pf.getField().contains("(")) {
                // Парсим агрегатную функцию
                AggregateFunctionInfo aggInfo = parseAggregateFunction(pf.getField());
                value = aggBuilder.translate(aggInfo.functionName, aggInfo.argument, aggInfo.distinct);
            } else if (pf.getField().equals("*")) {
                continue;
            } else {
                String source = pf.getSource() != null ? pf.getSource() + "." : "";
                value = "\"$" + source + pf.getField() + "\"";
            }

            fields.add(indent() + name + ": " + value);
        }

        project.append(String.join(",\n", fields));
        decreaseIndent();
        project.append("\n").append(indent()).append("} }");

        return project.toString();
    }

    public String generateFindProjection() {
        if (ir.getProjectionFields().isEmpty()) {
            return "";
        }

        boolean includeAll = ir.getProjectionFields().stream()
                .anyMatch(ProjectionField::isAllFields);

        if (includeAll) {
            return "{}";
        }

        StringBuilder proj = new StringBuilder("{ ");

        boolean hasId = ir.getProjectionFields().stream()
                .anyMatch(f -> "_id".equals(f.getField()) || "_id".equals(f.getAlias()));

        if (!hasId) {
            proj.append("_id: 0, ");
        }

        List<String> fields = new ArrayList<>();
        for (ProjectionField pf : ir.getProjectionFields()) {
            String name = FieldHelper.getDisplayName(pf);
            if (!pf.getField().equals("*")) {
                fields.add(name + ": 1");
            }
        }

        proj.append(String.join(", ", fields));
        proj.append(" }");

        return proj.toString();
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

    private record AggregateFunctionInfo(String functionName, String argument, boolean distinct) {
    }
}
