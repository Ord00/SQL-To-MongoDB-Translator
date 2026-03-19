package sql.to.mongodb.translator.service.code.generator.stages;

import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator;
import sql.to.mongodb.translator.service.code.generator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.CorrelationCondition;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator.getConditionNode;

public class SubqueryStageGenerator extends BaseGenerator {

    private final ConditionTranslator conditionTranslator;
    private final GroupStageGenerator groupGenerator;
    private final ProjectStageGenerator projectGenerator;

    public SubqueryStageGenerator(SqlToMongoIR ir, GenerationContext context) {
        super(ir, context);
        this.conditionTranslator = new ConditionTranslator(ir, context);
        this.groupGenerator = new GroupStageGenerator(ir, context);
        this.projectGenerator = new ProjectStageGenerator(ir, context);
    }

    @Override
    public String generate() throws CodeGenerationException {
        List<String> stages = new ArrayList<>();

        for (SubqueryInfo subquery : ir.getSubqueries()) {
            if (SubqueryHelper.isCorrelated(subquery)) {
                String stage = generateCorrelatedSubquery(subquery);
                if (stage != null) stages.add(stage);
            }
        }

        return String.join(",\n", stages);
    }

    public List<String> generateProjectionSubqueries() throws CodeGenerationException {
        List<String> stages = new ArrayList<>();

        for (SubqueryInfo subquery : ir.getSubqueries()) {
            if (!SubqueryHelper.isCorrelated(subquery)) {
                String stage = generateIndependentSubquery(subquery);
                stages.add(stage);
            }
        }

        return stages;
    }

    private String generateCorrelatedSubquery(SubqueryInfo subquery) throws CodeGenerationException {
        SqlToMongoIR subIR = subquery.getSubqueryIR();
        List<CorrelationCondition> correlations = subquery.getCorrelations();

        if (correlations.isEmpty()) return null;

        String subqueryName = context.nextSubqueryName();
        String fromCollection = SubqueryHelper.getSubqueryCollection(subIR);

        List<String> pipeline = new ArrayList<>();

        // $match для корреляции
        String matchStage = generateCorrelationMatch(correlations);
        if (matchStage != null) pipeline.add(matchStage);

        // Дополнительные стадии подзапроса
        if (!subIR.getWhereConditions().isEmpty()) {
            context.setUseAggregationSyntax(true);
            ConditionNode whereRoot = combineConditions(subIR.getWhereConditions());
            String whereStage = indent() + "{ $match: " +
                    conditionTranslator.translate(whereRoot) + " }";
            pipeline.add(whereStage);
            context.setUseAggregationSyntax(false);
        }

        if (subIR.isHasGroupBy()) {
            String groupStage = groupGenerator.generate();
            if (!groupStage.isEmpty()) pipeline.add(groupStage);
        }

        String projectStage = generateSubqueryProjection(subIR);
        if (projectStage != null) pipeline.add(projectStage);

        return buildLookupWithPipeline(fromCollection, correlations, pipeline, subqueryName);
    }

    private String generateIndependentSubquery(SubqueryInfo subquery) throws CodeGenerationException {
        SqlToMongoIR subIR = subquery.getSubqueryIR();
        String subqueryName = context.nextSubqueryName();
        String fromCollection = SubqueryHelper.getSubqueryCollection(subIR);

        List<String> pipeline = new ArrayList<>();

        if (!subIR.getWhereConditions().isEmpty()) {
            context.setUseAggregationSyntax(true);
            ConditionNode whereRoot = combineConditions(subIR.getWhereConditions());
            String whereStage = indent() + "{ $match: " +
                    conditionTranslator.translate(whereRoot) + " }";
            pipeline.add(whereStage);
            context.setUseAggregationSyntax(false);
        }

        if (subIR.isHasGroupBy()) {
            String groupStage = groupGenerator.generate();
            if (!groupStage.isEmpty()) pipeline.add(groupStage);
        }

        String projectStage = generateSubqueryProjection(subIR);
        if (projectStage != null) pipeline.add(projectStage);

        String lookup = buildSimpleLookup(fromCollection, pipeline, subqueryName);

        if (SubqueryHelper.isScalarSubquery(subquery)) {
            String unwind = generateUnwind(subqueryName);
            return lookup + ",\n" + unwind;
        }

        return lookup;
    }

    private String generateCorrelationMatch(List<CorrelationCondition> correlations) {
        if (correlations.isEmpty()) return null;

        StringBuilder match = new StringBuilder(indent() + "{ $match: {\n");
        increaseIndent();
        match.append(indent()).append("$expr: { $and: [\n");
        increaseIndent();

        for (int i = 0; i < correlations.size(); i++) {
            CorrelationCondition corr = correlations.get(i);
            String outerField = corr.getOuterField().split("\\.")[1];
            String innerField = corr.getInnerField();

            match.append(indent()).append("{ $eq: [ \"$").append(innerField)
                    .append("\", \"$$").append(outerField).append("\" ] }");

            if (i < correlations.size() - 1) {
                match.append(",\n");
            }
        }

        decreaseIndent();
        match.append("\n").append(indent()).append("]\n");
        decreaseIndent();
        match.append(indent()).append("} }");

        return match.toString();
    }

    private String generateSubqueryProjection(SqlToMongoIR subIR) throws CodeGenerationException {
        if (subIR.getProjectionFields().isEmpty()) {
            return null;
        }

        return projectGenerator.generate();
    }

    private String buildLookupWithPipeline(String from, List<CorrelationCondition> correlations,
                                           List<String> pipeline, String as) {
        StringBuilder lookup = new StringBuilder(indent() + "{ $lookup: {\n");
        increaseIndent();
        lookup.append(indent()).append("from: \"").append(from).append("\",\n");
        lookup.append(indent()).append("let: { ")
                .append(SubqueryHelper.buildLetVariables(correlations))
                .append(" },\n");

        if (!pipeline.isEmpty()) {
            lookup.append(indent()).append("pipeline: [\n");
            increaseIndent();
            lookup.append(String.join(",\n", pipeline));
            decreaseIndent();
            lookup.append("\n").append(indent()).append("],\n");
        }

        lookup.append(indent()).append("as: \"").append(as).append("\"\n");
        decreaseIndent();
        lookup.append(indent()).append("} }");

        return lookup.toString();
    }

    private String buildSimpleLookup(String from, List<String> pipeline, String as) {
        StringBuilder lookup = new StringBuilder(indent() + "{ $lookup: {\n");
        increaseIndent();
        lookup.append(indent()).append("from: \"").append(from).append("\",\n");

        if (!pipeline.isEmpty()) {
            lookup.append(indent()).append("pipeline: [\n");
            increaseIndent();
            lookup.append(String.join(",\n", pipeline));
            decreaseIndent();
            lookup.append("\n").append(indent()).append("],\n");
        }

        lookup.append(indent()).append("as: \"").append(as).append("\"\n");
        decreaseIndent();
        lookup.append(indent()).append("} }");

        return lookup.toString();
    }

    private String generateUnwind(String path) {
        StringBuilder unwind = new StringBuilder(indent() + "{ $unwind: {\n");
        increaseIndent();
        unwind.append(indent()).append("path: \"$").append(path).append("\",\n");
        unwind.append(indent()).append("preserveNullAndEmptyArrays: true\n");
        decreaseIndent();
        unwind.append(indent()).append("} }");
        return unwind.toString();
    }

    /**
     * Генерация pipeline для подзапроса в виде строки
     */
    public String generateSubqueryPipeline(SubqueryInfo subquery) throws CodeGenerationException {
        SqlToMongoIR subIR = subquery.getSubqueryIR();
        List<String> stages = new ArrayList<>();

        // WHERE
        if (!subIR.getWhereConditions().isEmpty()) {
            ConditionNode whereRoot = combineConditions(subIR.getWhereConditions());
            String whereStage = "{ $match: " +
                    conditionTranslator.translate(whereRoot) + " }";
            stages.add(whereStage);
        }

        // GROUP BY
        if (subIR.isHasGroupBy()) {
            String groupStage = groupGenerator.generate();
            if (!groupStage.isEmpty()) {
                stages.add(groupStage);
            }
        }

        // Проекция
        if (!subIR.getProjectionFields().isEmpty()) {
            if (subquery.getType() == SubqueryInfo.SubqueryType.SCALAR &&
                    subIR.getProjectionFields().size() == 1) {
                stages.add("{ $limit: 1 }");

                ProjectionField pf = subIR.getProjectionFields().getFirst();
                String project = "{ $project: { _id: 0, result: \"$" + pf.getField() + "\" } }";
                stages.add(project);
            } else {
                String projectStage = projectGenerator.generate();
                if (!projectStage.isEmpty()) {
                    stages.add(projectStage);
                }
            }
        }

        return "[\n" + stages.stream()
                .map(s -> "    " + s)
                .collect(Collectors.joining(",\n")) + "\n  ]";
    }

    private ConditionNode combineConditions(List<ConditionNode> conditions) {
        return getConditionNode(conditions);
    }
}
