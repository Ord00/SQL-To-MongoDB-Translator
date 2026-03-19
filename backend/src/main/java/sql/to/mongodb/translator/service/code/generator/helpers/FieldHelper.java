package sql.to.mongodb.translator.service.code.generator.helpers;

import lombok.Getter;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SortField;
import sql.to.mongodb.translator.service.intermediate.representation.details.JoinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class for field operations and JOIN condition parsing
 */
public class FieldHelper {

    /**
     * Gets full field name including source
     */
    public static String getFullFieldName(ProjectionField field) {
        if (field == null) return "";

        if (field.getSource() != null && !field.getSource().isEmpty()) {
            return field.getSource() + "." + field.getField();
        }
        return field.getField();
    }

    /**
     * Gets full field name from SortField
     */
    public static String getFullFieldName(SortField field) {
        return field != null ? field.getFullField() : "";
    }

    /**
     * Gets display name (alias or field name)
     */
    public static String getDisplayName(ProjectionField field) {
        if (field == null) return "";
        return field.getAlias() != null ? field.getAlias() : field.getField();
    }

    /**
     * Extracts join fields from JOIN condition
     * @param join Join information containing condition
     * @return String array with [localField, foreignField]
     */
    public static String[] extractJoinFields(JoinInfo join) {
        if (join == null) return new String[] { "id", "id" };

        ConditionNode condition = join.getJoinCondition();
        if (condition == null) return new String[] { "id", "id" };

        return extractFieldsFromCondition(condition, join.getLeftAlias(), join.getRightAlias());
    }

    /**
     * Extracts fields from condition node
     */
    private static String[] extractFieldsFromCondition(ConditionNode condition,
                                                       String leftAlias,
                                                       String rightAlias) {
        if (condition == null) return new String[] { "id", "id" };

        // Handle simple comparison (field = field)
        if (condition.getType() == ConditionNode.ConditionType.COMPARISON) {
            String field1 = condition.getField();
            Object value = condition.getValue();

            if (value instanceof String field2) {
                return determineLeftRightFields(field1, field2, leftAlias, rightAlias);
            }
        }

        // Handle AND conditions (common in JOINs)
        if (condition.getType() == ConditionNode.ConditionType.AND) {
            List<String[]> allFields = new ArrayList<>();
            for (ConditionNode child : condition.getChildren()) {
                String[] fields = extractFieldsFromCondition(child, leftAlias, rightAlias);
                if (fields != null) {
                    allFields.add(fields);
                }
            }

            // For multiple conditions, we need to combine them
            // This is a simplified approach - in real implementation you'd need to handle multiple fields
            if (!allFields.isEmpty()) {
                return allFields.getFirst(); // Return first condition for simplicity
            }
        }

        return new String[] { "id", "id" };
    }

    /**
     * Determines which field belongs to left/right table
     */
    private static String[] determineLeftRightFields(String field1, String field2,
                                                     String leftAlias, String rightAlias) {
        String leftField = null;
        String rightField = null;

        // Parse field1
        FieldInfo info1 = parseField(field1);
        // Parse field2
        FieldInfo info2 = parseField(field2);

        // Determine which is left and which is right based on aliases
        if (info1.table != null) {
            if (info1.table.equals(leftAlias)) {
                leftField = info1.column;
            } else if (info1.table.equals(rightAlias)) {
                rightField = info1.column;
            }
        }

        if (info2.table != null) {
            if (info2.table.equals(leftAlias)) {
                leftField = info2.column;
            } else if (info2.table.equals(rightAlias)) {
                rightField = info2.column;
            }
        }

        // If we couldn't determine by alias, try to guess by position
        if (leftField == null && rightField == null) {
            leftField = info1.column != null ? info1.column : field1;
            rightField = info2.column != null ? info2.column : field2;
        } else if (leftField == null) {
            leftField = rightField;
            rightField = null;
        } else if (rightField == null) {
            rightField = leftField;
            leftField = null;
        }

        return new String[] {
                leftField != null ? leftField : "id",
                rightField != null ? rightField : "id"
        };
    }

    /**
     * Parses field string into table and column parts
     */
    public static FieldInfo parseField(String field) {
        FieldInfo info = new FieldInfo();

        if (field == null || field.isEmpty()) {
            return info;
        }

        // Remove quotes if present
        String cleanField = field.replace("'", "").replace("\"", "");

        // Check for table.column format
        if (cleanField.contains(".")) {
            String[] parts = cleanField.split("\\.");
            if (parts.length >= 2) {
                info.table = parts[0];
                info.column = parts[1];
                // Handle cases like schema.table.column
                if (parts.length > 2) {
                    info.schema = parts[0];
                    info.table = parts[1];
                    info.column = parts[2];
                }
            }
        } else {
            info.column = cleanField;
        }

        return info;
    }

    /**
     * Builds MongoDB field path
     */
    public static String buildMongoFieldPath(FieldInfo info, boolean useAggregationSyntax) {
        StringBuilder path = new StringBuilder();

        if (useAggregationSyntax) {
            path.append("$");
        }

        if (info.schema != null) {
            path.append(info.schema).append(".");
        }
        if (info.table != null) {
            path.append(info.table).append(".");
        }
        path.append(info.column);

        return path.toString();
    }

    /**
     * Escapes field name for MongoDB
     */
    public static String escapeField(String field) {
        if (field == null) return "";

        // Replace dots with __ for aggregation syntax
        return field.replace(".", "__");
    }

    /**
     * Extracts column name from qualified field
     */
    public static String extractColumnName(String field) {
        if (field == null) return "";

        FieldInfo info = parseField(field);
        return info.column != null ? info.column : field;
    }

    /**
     * Extracts table name from qualified field
     */
    public static String extractTableName(String field) {
        if (field == null) return "";

        FieldInfo info = parseField(field);
        return info.table != null ? info.table : "";
    }

    /**
     * Validates if field name is valid
     */
    public static boolean isValidFieldName(String field) {
        if (field == null || field.isEmpty()) return false;

        // Check for invalid characters
        Pattern invalidPattern = Pattern.compile("[^a-zA-Z0-9_.]");
        return !invalidPattern.matcher(field).find();
    }

    /**
     * Inner class for field information
     */
    @Getter
    public static class FieldInfo {
        private String schema;
        private String table;
        private String column;

        public FieldInfo() {}

        public FieldInfo(String column) {
            this.column = column;
        }

        public FieldInfo(String table, String column) {
            this.table = table;
            this.column = column;
        }

        public FieldInfo(String schema, String table, String column) {
            this.schema = schema;
            this.table = table;
            this.column = column;
        }

        public boolean hasTable() { return table != null && !table.isEmpty(); }
        public boolean hasSchema() { return schema != null && !schema.isEmpty(); }

        @Override
        public String toString() {
            if (schema != null) {
                return schema + "." + table + "." + column;
            } else if (table != null) {
                return table + "." + column;
            } else {
                return column;
            }
        }
    }
}