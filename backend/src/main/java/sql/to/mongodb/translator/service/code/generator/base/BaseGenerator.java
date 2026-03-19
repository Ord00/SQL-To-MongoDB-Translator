package sql.to.mongodb.translator.service.code.generator.base;

import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;

public abstract class BaseGenerator {

    protected SqlToMongoIR ir;
    protected GenerationContext context;

    protected String indent() {
        return "  ".repeat(context.getIndentLevel());
    }

    protected void increaseIndent() {
        context.setIndentLevel(context.getIndentLevel() + 1);
    }

    protected void decreaseIndent() {
        context.setIndentLevel(context.getIndentLevel() - 1);
    }

    public abstract String generate(SqlToMongoIR ir,
                                    GenerationContext context) throws CodeGenerationException;
}
