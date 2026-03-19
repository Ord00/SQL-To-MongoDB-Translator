package sql.to.mongodb.translator.service.code.generator;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator;
import sql.to.mongodb.translator.service.code.generator.helpers.FormatHelper;
import sql.to.mongodb.translator.service.code.generator.stages.GroupStageGenerator;
import sql.to.mongodb.translator.service.code.generator.stages.LookupStageGenerator;
import sql.to.mongodb.translator.service.code.generator.stages.MatchStageGenerator;
import sql.to.mongodb.translator.service.code.generator.stages.ProjectStageGenerator;
import sql.to.mongodb.translator.service.code.generator.stages.SortStageGenerator;
import sql.to.mongodb.translator.service.code.generator.stages.SubqueryStageGenerator;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.*;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;

import java.util.*;

import static sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator.getConditionNode;

/**
 * Генератор MongoDB кода из промежуточного представления
 * Поддерживает два режима: find() и aggregate()
 */
@Component
public class CodeGenerator {

    private SqlToMongoIR ir;
    private GenerationContext context;
    private final StringBuilder output = new StringBuilder();

    // Генераторы стадий
    private final MatchStageGenerator matchGenerator;
    private final LookupStageGenerator lookupGenerator;
    private final GroupStageGenerator groupGenerator;
    private final ProjectStageGenerator projectGenerator;
    private final SortStageGenerator sortGenerator;
    private final SubqueryStageGenerator subqueryGenerator;
    private final ConditionTranslator conditionTranslator;

    public CodeGenerator(MatchStageGenerator matchGenerator,
                         LookupStageGenerator lookupGenerator,
                         GroupStageGenerator groupGenerator,
                         ProjectStageGenerator projectGenerator,
                         SortStageGenerator sortGenerator,
                         SubqueryStageGenerator subqueryGenerator,
                         ConditionTranslator conditionTranslator) {
        this.matchGenerator = matchGenerator;
        this.lookupGenerator = lookupGenerator;
        this.groupGenerator = groupGenerator;
        this.projectGenerator = projectGenerator;
        this.sortGenerator = sortGenerator;
        this.subqueryGenerator = subqueryGenerator;
        this.conditionTranslator = conditionTranslator;
    }

    public String generate(SqlToMongoIR ir) throws CodeGenerationException {

        this.ir = ir;
        this.context = new GenerationContext();

        output.setLength(0);

        if (ir == null) {
            throw new CodeGenerationException("IR cannot be null");
        }

        if (ir.getMainCollection() == null) {
            throw new CodeGenerationException("Main collection not specified");
        }

        if (requiresAggregation()) {
            generateAggregationPipeline();
        } else {
            generateFindQuery();
        }

        return output.toString();
    }

    private boolean requiresAggregation() {
        return ir.isRequiresAggregation() || ir.isHasJoins() || ir.isHasSubqueries() ||
                ir.isHasGroupBy() || ir.isHasComplexProjections();
    }

    private void generateFindQuery() throws CodeGenerationException {
        String collection = FormatHelper.escapeIdentifier(ir.getMainCollection());

        output.append("db.").append(collection).append(".find(");

        // Query part
        String query = generateQueryDocument();
        output.append(query.isEmpty() ? "{}" : query);

        // Projection part
        String projection = projectGenerator.generateFindProjection();
        if (!projection.isEmpty()) {
            output.append(", ").append(projection);
        }
        output.append(")");

        // Sort
        String sort = sortGenerator.generateFindSort();
        if (!sort.isEmpty()) {
            output.append(".sort(").append(sort).append(")");
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

    private void generateAggregationPipeline() throws CodeGenerationException {
        String collection = FormatHelper.escapeIdentifier(ir.getMainCollection());
        output.append("db.").append(collection).append(".aggregate([\n");
        context.setIndentLevel(1);

        List<String> stages = new ArrayList<>();

        // Коррелированные подзапросы
        String correlatedSubqueries = subqueryGenerator.generate(ir, context);
        if (!correlatedSubqueries.isEmpty()) {
            stages.add(correlatedSubqueries);
        }

        // $match (WHERE)
        String matchStage = matchGenerator.generate(ir, context);
        if (!matchStage.isEmpty()) {
            stages.add(matchStage);
        }

        // $lookup (JOINs)
        if (ir.isHasJoins()) {
            stages.addAll(lookupGenerator.generateLookupStages());
        }

        // $match для условий JOIN
        if (ir.isHasJoins()) {
            List<ConditionNode> joinConditions = extractJoinConditions();
            String joinMatch = matchGenerator.generateJoinMatchStage(joinConditions);
            if (!joinMatch.isEmpty()) {
                stages.add(joinMatch);
            }
        }

        // Подзапросы в проекции
        stages.addAll(subqueryGenerator.generateProjectionSubqueries());

        // $group
        String groupStage = groupGenerator.generate(ir, context);
        if (!groupStage.isEmpty()) {
            stages.add(groupStage);
        }

        // $match (HAVING)
        String havingStage = matchGenerator.generateHavingStage();
        if (!havingStage.isEmpty()) {
            stages.add(havingStage);
        }

        // $sort
        String sortStage = sortGenerator.generate(ir, context);
        if (!sortStage.isEmpty()) {
            stages.add(sortStage);
        }

        // $skip/$limit
        if (ir.getOffset() != null) {
            stages.add(context.getIndent() + "{ $skip: " + ir.getOffset() + " }");
        }
        if (ir.getLimit() != null) {
            stages.add(context.getIndent() + "{ $limit: " + ir.getLimit() + " }");
        }

        // $project
        String projectStage = projectGenerator.generate(ir, context);
        if (!projectStage.isEmpty()) {
            stages.add(projectStage);
        }

        output.append(String.join(",\n", stages));
        output.append("\n");
        context.setIndentLevel(0);
        output.append("]);");
    }

    private String generateQueryDocument() throws CodeGenerationException {
        if (ir.getWhereConditions().isEmpty()) {
            return "";
        }

        context.setUseAggregationSyntax(false);
        ConditionNode root = combineConditions(ir.getWhereConditions());
        String result = conditionTranslator.translate(root, context);
        context.setUseAggregationSyntax(false);

        return result;
    }

    private List<ConditionNode> extractJoinConditions() {
        List<ConditionNode> conditions = new ArrayList<>();
        if (ir.getJoins() != null) {
            for (JoinInfo join : ir.getJoins()) {
                if (join.getJoinCondition() != null) {
                    conditions.add(join.getJoinCondition());
                }
            }
        }
        return conditions;
    }

    private ConditionNode combineConditions(List<ConditionNode> conditions) {
        return getConditionNode(conditions);
    }
}