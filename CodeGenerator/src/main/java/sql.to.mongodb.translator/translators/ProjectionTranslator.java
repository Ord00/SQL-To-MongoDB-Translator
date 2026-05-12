package sql.to.mongodb.translator.translators;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.base.GenerationContext;
import sql.to.mongodb.translator.helpers.SubqueryHelper;
import sql.to.mongodb.translator.ir.projection.AggregateProjection;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.CaseProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.ir.projection.Projectionable;
import sql.to.mongodb.translator.ir.projection.SubqueryProjection;

import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.DOWN;
import static sql.to.mongodb.translator.helpers.FormatHelper.IndentChangeType.NONE;
import static sql.to.mongodb.translator.helpers.FormatHelper.indent;

@Component
public class ProjectionTranslator {

    private final ExpressionTranslator expressionTranslator;
    private final SubqueryHelper subqueryHelper;

    public ProjectionTranslator(ExpressionTranslator expressionTranslator,
                                SubqueryHelper subqueryHelper) {
        this.expressionTranslator = expressionTranslator;
        this.subqueryHelper = subqueryHelper;
    }

    public String translate(Projectionable projection,
                            GenerationContext context,
                            boolean isFind) throws CodeGenerationException {
        return switch (projection) {
            case ProjectionField field -> translateField(field, context, isFind);
            case AggregateProjection agg -> translateAggregate(agg, context);
            case ArithmeticProjection arith -> translateArithmetic(arith, context);
            case CaseProjection caseProj -> translateCase(caseProj, context);
            case SubqueryProjection subq -> translateSubquery(subq, context);
            case null, default -> "";
        };

    }

    private String translateField(ProjectionField field,
                                  GenerationContext context,
                                  boolean isFind) {
        if (isFind) {
            String name = field.getAlias() != null ? field.getAlias() : field.getField();
            return name + ": 1";
        }
        String fieldPath = context.resolveFieldPath(field.getAlias(), field.getField());
        String name = field.getAlias() != null ? field.getAlias() : field.getField();
        return indent(NONE, context) + name + ": \"$" + fieldPath + "\"";
    }

    private String translateAggregate(AggregateProjection agg, GenerationContext context) {
        String name = agg.getAlias() != null ? agg.getAlias() : agg.getType().name().toLowerCase();
        String fieldPath = context.resolveFieldPath(agg.getField().getSource(), agg.getField().getField());

        StringBuilder expr =  new StringBuilder();
        switch (agg.getType()) {
            case COUNT:
                if (agg.isDistinct()) {
                    expr.append("{\n");
                    context.increaseIndent();
                    expr.append(indent(DOWN, context)).append("$addToSet: \"$").append(fieldPath).append("\"\n");
                    expr.append(indent(NONE, context)).append("}");
                } else if (fieldPath == null || "*".equals(fieldPath)) {
                    expr.append("{ $sum: 1 }");
                } else {
                    expr.append("{ $sum: 1 }");
                }
                break;
            case SUM: expr.append("{ $sum: \"$").append(fieldPath).append("\" }"); break;
            case AVG: expr.append("{ $avg: \"$").append(fieldPath).append("\" }"); break;
            case MIN: expr.append("{ $min: \"$").append(fieldPath).append("\" }"); break;
            case MAX: expr.append("{ $max: \"$").append(fieldPath).append("\" }"); break;
            default: expr.append("{ $first: \"$$ROOT\" }");
        }
        return indent(NONE, context) + name + ": " + expr;
    }

    private String translateArithmetic(ArithmeticProjection arith,
                                       GenerationContext context) throws CodeGenerationException {
        String name = arith.getAlias() != null ? arith.getAlias() : "computed";
        String expr = expressionTranslator.translate(arith.getExpression(), context);
        return indent(NONE, context) + name + ": " + expr;
    }

    private String translateCase(CaseProjection caseProj,
                                 GenerationContext context) throws CodeGenerationException {
        String name = caseProj.getAlias() != null ? caseProj.getAlias() : "case_result";
        String expr = expressionTranslator.translate(caseProj.getExpression(), context);
        return indent(NONE, context) + name + ": " + expr;
    }

    private String translateSubquery(SubqueryProjection subq, GenerationContext context) {
        String name = subq.getAlias() != null ? subq.getAlias() : "subquery";
        boolean correlated = subqueryHelper.isCorrelated(subq);

        if (correlated) {
            String subqueryName = context.getSubqueryName(subq);
            if (subqueryName == null || subqueryName.isBlank()) {
                subqueryName = context.getOrCreateSubqueryName(subq);
            }
            return indent(NONE, context)
                    + name
                    + ": { $ifNull: [ { $arrayElemAt: [ \"$"
                    + subqueryName
                    + ".result\", 0 ] }, null ] }";
        }
        return indent(NONE, context) + name + ": null";
    }
}
