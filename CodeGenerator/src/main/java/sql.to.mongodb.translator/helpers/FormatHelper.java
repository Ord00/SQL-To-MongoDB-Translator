package sql.to.mongodb.translator.helpers;

import sql.to.mongodb.translator.base.GenerationContext;

public class FormatHelper {

    public static String formatValue(Object value, GenerationContext context) {
        switch (value) {
            case null -> {
                return "null";
            }
            case String str -> {
                if (str.startsWith("'") && str.endsWith("'")) {
                    return str;
                }
                if (str.contains(".") && context.isUseAggregationSyntax()) {
                    return "\"$" + str + "\"";
                }
                return "\"" + str + "\"";
            }
            case Number ignored -> {
                return value.toString();
            }
            case Boolean ignored -> {
                return value.toString().toLowerCase();
            }
            default -> {
            }
        }

        return String.valueOf(value);
    }

    public static String indent(GenerationContext context) {
        return "    ".repeat(Math.max(0, context.getIndentLevel()));
    }
}
