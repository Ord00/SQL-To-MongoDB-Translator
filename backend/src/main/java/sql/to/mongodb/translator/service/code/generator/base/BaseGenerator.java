package sql.to.mongodb.translator.service.code.generator.base;

import sql.to.mongodb.translator.service.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.service.intermediate.representation.SqlToMongoIR;

public abstract class BaseGenerator {

    protected final SqlToMongoIR ir;
    protected final GenerationContext context;

    public BaseGenerator(SqlToMongoIR ir, GenerationContext context) {
        this.ir = ir;
        this.context = context;
    }

    protected String indent() {
        return "  ".repeat(context.getIndentLevel());
    }

    protected void increaseIndent() {
        context.setIndentLevel(context.getIndentLevel() + 1);
    }

    protected void decreaseIndent() {
        context.setIndentLevel(context.getIndentLevel() - 1);
    }

    public abstract String generate() throws CodeGenerationException;
}
