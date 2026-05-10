package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.translators.ConditionTranslator;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExistsSubqueryBuilder {

    private final ConditionTranslator conditionTranslator;
    private final LookupStageBuilder lookupStageBuilder;
    private final PipelineBuilder pipelineBuilder;
    private final ProjectStageBuilder projectStageBuilder;

    public ExistsSubqueryBuilder(ConditionTranslator conditionTranslator,
                                 LookupStageBuilder lookupStageBuilder,
                                 PipelineBuilder pipelineBuilder,
                                 ProjectStageBuilder projectStageBuilder) {
        this.conditionTranslator = conditionTranslator;
        this.lookupStageBuilder = lookupStageBuilder;
        this.pipelineBuilder = pipelineBuilder;
        this.projectStageBuilder = projectStageBuilder;
    }

    public record ExistsSubqueryResult(List<String> stages, String arrayName) { }

    public ExistsSubqueryResult build(CorrelationSubquery subquery,
                                      boolean isExists,
                                      GenerationContext context) throws CodeGenerationException {

        SqlToMongoIR subIR = subquery.getSubqueryIR();
        String subqueryName = context.nextSubqueryName();

        List<String> stages = new ArrayList<>();

        boolean originalSyntax = context.isUseAggregationSyntax();
        context.setUseAggregationSyntax(true);
        context.enterSubquery();

        try {
            // Строим pipeline для подзапроса через PipelineBuilder
            List<String> pipelineStages = pipelineBuilder.buildStages(subIR, context);

            String project = projectStageBuilder.buildForSubquery(subIR, context);
            if (project != null) {
                pipelineStages.add(project);
            }

            // Создаём $lookup с let и pipeline
            String lookup = lookupStageBuilder.buildLookupWithPipeline(
                    subIR.getMainCollection(),
                    subqueryName,
                    pipelineStages,
                    subquery.getCorrelations(),
                    context
            );
            stages.add(lookup);

            // Добавляем $match для NOT EXISTS (размер массива = 0)
            String match = buildExistsMatch(subqueryName, isExists, context);
            stages.add(match);

        } finally {
            context.exitSubquery();
            context.setUseAggregationSyntax(originalSyntax);
        }

        return new ExistsSubqueryResult(stages, subqueryName);
    }

    private String buildExistsMatch(String arrayName, boolean isExists, GenerationContext context) {
        String indent = context.getIndent();
        if (isExists) {
            return indent + "{\n"
                    + indent + "    $match: {\n"
                    + indent + "        $expr: { $gt: [ { $size: \"$" + arrayName + "\" }, 0 ] }\n"
                    + indent + "    }\n"
                    + indent + "}";
        } else {
            return indent + "{\n"
                    + indent + "    $match: {\n"
                    + indent + "        $expr: { $eq: [ { $size: \"$" + arrayName + "\" }, 0 ] }\n"
                    + indent + "    }\n"
                    + indent + "}";
        }
    }
}