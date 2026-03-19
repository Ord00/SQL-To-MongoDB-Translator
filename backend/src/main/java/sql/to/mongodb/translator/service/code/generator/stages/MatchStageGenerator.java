package sql.to.mongodb.translator.service.code.generator.stages;

import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;

import java.util.List;

import static sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator.getConditionNode;

public class MatchStageGenerator extends BaseGenerator {

    private final ConditionTranslator conditionTranslator;

    public MatchStageGenerator(SqlToMongoIR ir, GenerationContext context) {
        super(ir, context);
        this.conditionTranslator = new ConditionTranslator(ir, context);
    }

    @Override
    public String generate() throws CodeGenerationException {
        if (ir.getWhereConditions().isEmpty()) {
            return "";
        }

        ConditionNode root = combineConditions(ir.getWhereConditions());
        String condition = conditionTranslator.translate(root);

        return indent() + "{ $match: " + condition + " }";
    }

    public String generateHavingStage() throws CodeGenerationException {
        if (ir.getHavingConditions().isEmpty()) {
            return "";
        }

        context.setUseAggregationSyntax(true);
        ConditionNode root = combineConditions(ir.getHavingConditions());
        String condition = conditionTranslator.translate(root);
        context.setUseAggregationSyntax(false);

        return indent() + "{ $match: " + condition + " }";
    }

    public String generateJoinMatchStage(List<ConditionNode> joinConditions) throws CodeGenerationException {
        if (joinConditions.isEmpty()) {
            return "";
        }

        context.setUseAggregationSyntax(true);
        ConditionNode root = combineConditions(joinConditions);
        String condition = conditionTranslator.translate(root);
        context.setUseAggregationSyntax(false);

        return indent() + "{ $match: " + condition + " }";
    }

    private ConditionNode combineConditions(List<ConditionNode> conditions) {
        return getConditionNode(conditions);
    }
}
