package sql.to.mongodb.translator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.builders.MatchStageBuilder;
import sql.to.mongodb.translator.builders.PipelineBuilder;
import sql.to.mongodb.translator.builders.ProjectStageBuilder;
import sql.to.mongodb.translator.builders.SortStageBuilder;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.ir.SqlToMongoIR;

import java.util.List;

@Component
public class CodeGenerator {

    private final PipelineBuilder pipelineBuilder;
    private final ProjectStageBuilder projectStageBuilder;
    private final SortStageBuilder sortStageBuilder;

    @Autowired
    public CodeGenerator(PipelineBuilder pipelineBuilder,
                         ProjectStageBuilder projectStageBuilder,
                         SortStageBuilder sortStageBuilder) {
        this.pipelineBuilder = pipelineBuilder;
        this.projectStageBuilder = projectStageBuilder;
        this.sortStageBuilder = sortStageBuilder;
    }

    public String generate(SqlToMongoIR ir) throws CodeGenerationException {
        GenerationContext context = new GenerationContext();

        if (ir == null || ir.getMainCollection() == null) {
            throw new CodeGenerationException("Invalid IR");
        }

        if (ir.isRequiresAggregation()) {
            return generateAggregationPipeline(ir, context);
        }
        return generateFindQuery(ir, context);
    }

    private String generateFindQuery(SqlToMongoIR ir,
                                     GenerationContext context) throws CodeGenerationException {
        StringBuilder query = new StringBuilder("db." + ir.getMainCollection() + ".find(");

        // WHERE - используем $match builder но с find синтаксисом
        context.setUseAggregationSyntax(false);
        var whereBuilder = new MatchStageBuilder(null);
        String where = whereBuilder.buildWhere(ir, context);
        query.append(where != null ? where.replace("{ $match: ", "")
                .replace("}", "")
                .trim() : "{}");

        // Projection
        String projection = projectStageBuilder.build(ir, context);
        if (projection != null && !"{}".equals(projection)) {
            query.append(", ").append(projection);
        }
        query.append(")");

        // Sort
        String sort = sortStageBuilder.buildFindSort(ir);
        if (sort != null) query.append(".sort(").append(sort).append(")");

        // Limit/Skip
        if (ir.getLimit() != null) query.append(".limit(").append(ir.getLimit()).append(")");
        if (ir.getOffset() != null) query.append(".skip(").append(ir.getOffset()).append(")");

        return query.append(";").toString();
    }

    private String generateAggregationPipeline(SqlToMongoIR ir,
                                               GenerationContext context) throws CodeGenerationException {
        StringBuilder pipeline = new StringBuilder("db." + ir.getMainCollection() + ".aggregate([\n");
        context.setIndentLevel(1);

        List<String> stages = pipelineBuilder.buildStages(ir, context);

        pipeline.append(String.join(",\n", stages));
        pipeline.append("\n]);");

        return pipeline.toString();
    }
}