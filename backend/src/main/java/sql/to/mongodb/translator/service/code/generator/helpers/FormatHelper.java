package sql.to.mongodb.translator.service.code.generator.helpers;

import sql.to.mongodb.translator.service.code.generator.base.GenerationContext;

public class FormatHelper {

    public static String formatValue(Object value, GenerationContext context) {
        switch (value) {
            case null -> {
                return "null";
            }
            case String str -> {
                if (str.startsWith("'") && str.endsWith("'")) {
                    return "\"" + str.substring(1, str.length() - 1) + "\"";
                } else if (str.contains(".")) {
                    if (context.isUseAggregationSyntax()) {
                        return "\"$" + str + "\"";
                    } else {
                        return str;
                    }
                }
                return "\"" + str + "\"";
            }
            case Number _, Boolean _ -> {
                return value.toString();
            }
            default -> {
            }
        }

        return String.valueOf(value);
    }

    public static String escapeIdentifier(String identifier) {
        return identifier; // Можно добавить экранирование при необходимости
    }

    public static String escapeField(String field, GenerationContext context) {
        if (field == null) return "";

        if (field.startsWith("'") && field.endsWith("'")) {
            field = field.substring(1, field.length() - 1);
        }

        if (context.isUseAggregationSyntax()) {
            return field.replace(".", "__");
        }

        return field;
    }
}
