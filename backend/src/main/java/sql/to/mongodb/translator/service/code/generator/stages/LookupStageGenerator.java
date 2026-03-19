package sql.to.mongodb.translator.service.code.generator.stages;

import sql.to.mongodb.translator.service.code.generator.base.BaseGenerator;
import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;
import sql.to.mongodb.translator.service.code.generator.helpers.FormatHelper;
import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.JoinInfo;

import java.util.ArrayList;
import java.util.List;

public class LookupStageGenerator extends BaseGenerator {

    public LookupStageGenerator(SqlToMongoIR ir, GenerationContext context) {
        super(ir, context);
    }

    @Override
    public String generate() throws CodeGenerationException {
        return String.join(",\n", generateLookupStages());
    }

    public List<String> generateLookupStages() {
        List<String> stages = new ArrayList<>();

        for (JoinInfo join : ir.getJoins()) {
            String from = FormatHelper.escapeIdentifier(join.getRightTable());
            String as = join.getRightAlias() != null ?
                    FormatHelper.escapeIdentifier(join.getRightAlias()) :
                    FormatHelper.escapeIdentifier(join.getRightTable());

            String[] fields = extractJoinFields(join);
            String localField = fields[0];
            String foreignField = fields[1];

            String lookup = generateLookup(from, localField, foreignField, as);
            stages.add(lookup);

            String unwind = generateUnwind(as, join.getType() == JoinInfo.JoinType.LEFT);
            stages.add(unwind);
        }

        return stages;
    }

    private String generateLookup(String from, String localField, String foreignField, String as) {
        StringBuilder lookup = new StringBuilder(indent() + "{ $lookup: {\n");
        increaseIndent();
        lookup.append(indent()).append("from: \"").append(from).append("\",\n");
        lookup.append(indent()).append("localField: \"").append(localField).append("\",\n");
        lookup.append(indent()).append("foreignField: \"").append(foreignField).append("\",\n");
        lookup.append(indent()).append("as: \"").append(as).append("\"\n");
        decreaseIndent();
        lookup.append(indent()).append("} }");
        return lookup.toString();
    }

    private String generateUnwind(String as, boolean preserveNull) {
        if (preserveNull) {
            StringBuilder unwind = new StringBuilder(indent() + "{ $unwind: {\n");
            increaseIndent();
            unwind.append(indent()).append("path: \"$").append(as).append("\",\n");
            unwind.append(indent()).append("preserveNullAndEmptyArrays: true\n");
            decreaseIndent();
            unwind.append(indent()).append("} }");
            return unwind.toString();
        } else {
            return indent() + "{ $unwind: \"$" + as + "\" }";
        }
    }

    private String[] extractJoinFields(JoinInfo join) {
        ConditionNode condition = join.getJoinCondition();
        if (condition != null) {
            String field1 = condition.getField();
            Object value = condition.getValue();

            if (value instanceof String field2) {

                if (field1.contains(".")) {
                    String[] parts1 = field1.split("\\.");
                    String[] parts2 = field2.split("\\.");
                    return new String[] { parts1[1], parts2[1] };
                }
            }
        }

        return new String[] { "id", "id" };
    }
}
