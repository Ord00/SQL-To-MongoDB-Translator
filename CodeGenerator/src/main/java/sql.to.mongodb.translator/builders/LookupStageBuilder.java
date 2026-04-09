package sql.to.mongodb.translator.builders;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.ir.CorrelationSubquery;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.CorrelationCondition;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinTable;
import sql.to.mongodb.translator.translators.ConditionTranslator;

import java.util.List;

@Component
public class LookupStageBuilder {

    private final ConditionTranslator conditionTranslator;
    private final SubqueryHelper subqueryHelper;
    private final GroupStageBuilder groupStageBuilder;
    private final ProjectStageBuilder projectStageBuilder;

    public LookupStageBuilder(ConditionTranslator conditionTranslator,
                              SubqueryHelper subqueryHelper,
                              GroupStageBuilder groupStageBuilder,
                              ProjectStageBuilder projectStageBuilder) {
        this.conditionTranslator = conditionTranslator;
        this.subqueryHelper = subqueryHelper;
        this.groupStageBuilder = groupStageBuilder;
        this.projectStageBuilder = projectStageBuilder;
    }

    public String buildSimpleLookup(JoinInfo join, GenerationContext context) {
        String rightTable = join.getRight() instanceof JoinTable t ? t.getValue() : "subquery";
        String as = join.getRight().getAlias() != null ? join.getRight().getAlias() : rightTable;

        StringBuilder lookup = new StringBuilder(indent(context) + "{ $lookup: {\n");
        context.increaseIndent();
        lookup.append(context.getIndent()).append("from: \"").append(rightTable).append("\",\n");
        lookup.append(context.getIndent()).append("localField: \"_id\",\n");
        lookup.append(context.getIndent()).append("foreignField: \"_id\",\n");
        lookup.append(context.getIndent()).append("as: \"").append(as).append("\"\n");
        context.decreaseIndent();
        lookup.append(indent(context)).append("} }");

        String unwind = buildUnwind(as, join.getType() == JoinInfo.JoinType.LEFT, context);
        return lookup + ",\n" + unwind;
    }

    public String buildCorrelatedSubqueryLookup(CorrelationSubquery subquery,
                                                String subqueryName,
                                                GenerationContext context) throws CodeGenerationException {
        String fromCollection = subquery.getSubqueryIR().getMainCollection();
        List<CorrelationCondition> correlations = subquery.getCorrelations();

        StringBuilder lookup = new StringBuilder(indent(context) + "{ $lookup: {\n");
        context.increaseIndent();
        lookup.append(context.getIndent())
                .append("from: \"")
                .append(fromCollection)
                .append("\",\n");
        lookup.append(context.getIndent())
                .append("let: { ")
                .append(subqueryHelper.buildLetVariables(correlations))
                .append(" },\n");
        lookup.append(context.getIndent()).append("pipeline: [\n");
        context.increaseIndent();

        String match = subqueryHelper.buildCorrelationMatch(correlations, context);
        if (match != null) {
            lookup.append(context.getIndent()).append(match).append(",\n");
        }

        SqlToMongoIR subIR = subquery.getSubqueryIR();
        if (subIR.getWhereCondition() != null) {
            context.setUseAggregationSyntax(true);
            String where = conditionTranslator.translate(subIR.getWhereCondition(), context);
            lookup.append(context.getIndent()).append("{ $match: ").append(where).append(" },\n");
        }

        if (subIR.isHasGroupBy()) {
            lookup.append(context.getIndent()).append(buildSubqueryGroup(subIR, context)).append(",\n");
        }

        String project = projectStageBuilder.buildForSubquery(subIR, context);
        if (project != null) {
            lookup.append(context.getIndent()).append(project).append("\n");
        } else {
            lookup.append(context.getIndent()).append("{ $project: { _id: 0, result: 1 } }\n");
        }

        context.decreaseIndent();
        lookup.append(context.getIndent()).append("],\n");
        lookup.append(context.getIndent()).append("as: \"").append(subqueryName).append("\"\n");
        context.decreaseIndent();
        lookup.append(indent(context)).append("} }");

        return lookup.toString();
    }

    private String buildSubqueryGroup(SqlToMongoIR subIR,
                                      GenerationContext context) {
        String group = groupStageBuilder.buildForSubquery(subIR, context);
        return group != null ? group : "";
    }

    public String buildUnwind(String path, boolean preserveNull, GenerationContext context) {
        if (preserveNull) {
            StringBuilder unwind = new StringBuilder(indent(context) + "{ $unwind: {\n");
            context.increaseIndent();
            unwind.append(context.getIndent()).append("path: \"$").append(path).append("\",\n");
            unwind.append(context.getIndent()).append("preserveNullAndEmptyArrays: true\n");
            context.decreaseIndent();
            unwind.append(indent(context)).append("} }");
            return unwind.toString();
        }
        return indent(context) + "{ $unwind: \"$" + path + "\" }";
    }

    private String indent(GenerationContext context) {
        return "  ".repeat(Math.max(0, context.getIndentLevel()));
    }
}