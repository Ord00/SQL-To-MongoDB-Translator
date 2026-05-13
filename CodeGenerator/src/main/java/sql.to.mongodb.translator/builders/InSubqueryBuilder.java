package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.condition.InCondition;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.Projectionable;
import sql.to.mongodb.translator.translators.ExpressionTranslator;

import java.util.ArrayList;
import java.util.List;

import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.DOWN;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.NONE;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.UP;
import static sql.to.mongodb.translator.helpers.FormatHelper.indent;

@Component
public class InSubqueryBuilder {

    private final ExpressionTranslator expressionTranslator;
    private final LookupStageBuilder lookupStageBuilder;
    private final ProjectStageBuilder projectStageBuilder;
    private final SortStageBuilder sortStageBuilder;

    public InSubqueryBuilder(ExpressionTranslator expressionTranslator,
                             LookupStageBuilder lookupStageBuilder,
                             ProjectStageBuilder projectStageBuilder,
                             SortStageBuilder sortStageBuilder) {
        this.expressionTranslator = expressionTranslator;
        this.lookupStageBuilder = lookupStageBuilder;
        this.projectStageBuilder = projectStageBuilder;
        this.sortStageBuilder = sortStageBuilder;
    }

    public record InSubqueryResult(String arrayPath, List<String> stages) { }

    public InSubqueryResult build(InCondition inCondition,
                                  GenerationContext context) throws CodeGenerationException {

        if (inCondition.getInValues().isEmpty() ||
                !(inCondition.getInValues().getFirst() instanceof Subquery subquery)) {
            return null;
        }

        SqlToMongoIR subIR = subquery.getSubqueryIR();
        if (subIR == null || subIR.getProjectionFields().isEmpty()) {
            return null;
        }

        Projectionable firstProjection = subIR.getProjectionFields().getFirst();
        String alias;
        String expressionStr;

        if (firstProjection instanceof ArithmeticProjection arith) {
            alias = arith.getAlias() != null ? arith.getAlias() : "result";
            expressionStr = expressionTranslator.translate(arith.getExpression(), context);
        } else if (firstProjection instanceof ProjectionField field) {
            alias = field.getAlias() != null ? field.getAlias() : field.getField();
            expressionStr = "\"$" + alias + "\"";
        } else {
            return null;
        }

        boolean originalSyntax = context.isUseAggregationSyntax();
        context.setUseAggregationSyntax(true);

        String subqueryName = context.nextSubqueryName();
        String arrayName = subqueryName + "Array";

        List<String> stages = new ArrayList<>();

        try {
            // Строим pipeline стадии для подзапроса
            List<String> pipelineStages = new ArrayList<>();


            // Project стадия
            for (int i = 0; i < 4; ++i) {
                context.increaseIndent();
            }

            String projectStage = "{\n"
                    + indent(UP, context) + "$project: {\n"
                    + indent(DOWN, context) + alias + ": " + expressionStr + "\n"
                    + indent(DOWN, context) + "}\n"
                    + indent(NONE, context) + "}";
            pipelineStages.add(projectStage);

            // Sort (если есть) - используем SortStageBuilder
            String sort = sortStageBuilder.build(subIR, context);
            if (sort != null) {
                pipelineStages.add(sort);
            }

            // Limit
            if (subIR.getLimit() != null) {
                pipelineStages.add("{ $limit: " + subIR.getLimit() + " }");
            }

            for (int i = 0; i < 3; ++i) {
                context.decreaseIndent();
            }

            // Используем LookupStageBuilder для создания $lookup
            String lookup = lookupStageBuilder.buildSimplePipelineLookup(
                    subIR.getMainCollection(),
                    subqueryName,
                    pipelineStages,
                    context
            );
            stages.add(lookup);

            // Используем ProjectStageBuilder для создания $addFields с $map
            String addFields = projectStageBuilder.buildAddFieldsWithMap(
                    arrayName,
                    subqueryName,
                    alias,
                    context
            );
            stages.add(addFields);

        } finally {
            context.setUseAggregationSyntax(originalSyntax);
        }

        return new InSubqueryResult("\"$" + arrayName + "\"", stages);
    }
}
