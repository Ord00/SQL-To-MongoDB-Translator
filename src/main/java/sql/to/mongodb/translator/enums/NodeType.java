package sql.to.mongodb.translator.enums;

public enum NodeType {
    UNDEFINED,
    QUERY,
    TERMINAL,
    COLUMN_NAMES,
    TABLE_NAMES,
    TABLE,
    JOIN,
    LOGICAL_CONDITION,
    OPERATOR
}
