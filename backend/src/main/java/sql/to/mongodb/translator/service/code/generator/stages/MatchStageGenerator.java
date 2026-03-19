package sql.to.mongodb.translator.service.code.generator.stages;

import org.springframework.stereotype.Component;
import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;

import java.util.List;

import static sql.to.mongodb.translator.service.code.generator.conditions.ConditionTranslator.getConditionNode;

@Component
public class MatchStageGenerator extends BaseGenerator {

    private final ConditionTranslator conditionTranslator;

    public MatchStageGenerator(ConditionTranslator conditionTranslator) {
        this.conditionTranslator = conditionTranslator;
    }

    @Override
    public String generate(SqlToMongoIR ir, GenerationContext context) throws CodeGenerationException {
        this.ir = ir;
        this.context = context;

        if (ir.getWhereConditions().isEmpty()) {
            return "";
        }

        ConditionNode root = combineConditions(ir.getWhereConditions());
        String condition = conditionTranslator.translate(root, context);

        return indent() + "{ $match: " + condition + " }";
    }

    public String generateHavingStage() throws CodeGenerationException {
        if (ir.getHavingConditions().isEmpty()) {
            return "";
        }

        context.setUseAggregationSyntax(true);
        ConditionNode root = combineConditions(ir.getHavingConditions());
        String condition = conditionTranslator.translate(root, context);
        context.setUseAggregationSyntax(false);

        return indent() + "{ $match: " + condition + " }";
    }

    public String generateJoinMatchStage(List<ConditionNode> joinConditions) throws CodeGenerationException {
        if (joinConditions.isEmpty()) {
            return "";
        }

        context.setUseAggregationSyntax(true);
        ConditionNode root = combineConditions(joinConditions);
        String condition = conditionTranslator.translate(root,  context);
        context.setUseAggregationSyntax(false);

        return indent() + "{ $match: " + condition + " }";
    }

    private ConditionNode combineConditions(List<ConditionNode> conditions) {
        return getConditionNode(conditions);
    }
}
