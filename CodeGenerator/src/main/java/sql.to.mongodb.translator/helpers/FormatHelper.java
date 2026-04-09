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
                    return "\"" + str.substring(1, str.length() - 1) + "\"";
                }
                if (str.contains(".") && context.isUseAggregationSyntax()) {
                    return "\"$" + str + "\"";
                }
                return "\"" + str + "\"";
            }
            case Number _ -> {
                return value.toString();
            }
            case Boolean _ -> {
                return value.toString().toLowerCase();
            }
            default -> {
            }
        }

        return String.valueOf(value);
    }
}
