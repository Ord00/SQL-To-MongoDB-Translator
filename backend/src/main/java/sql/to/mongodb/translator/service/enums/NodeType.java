package sql.to.mongodb.translator.service.enums;

public enum NodeType {
    UNDEFINED,
    QUERY,
    TERMINAL,
    COLUMN_NAMES,
    TABLE_NAMES,
    TABLE,
    JOIN,
    LOGICAL_CONDITION,
    LOGICAL_CHECK,
    OPERATOR,
    ATTRIBUTES,
    IDENTIFIER,
    GROUP_BY,
    ARITHMETIC_EXP,
    AGGREGATE,
    ORDER_BY,
    CASE,
    CASE_PART
}
