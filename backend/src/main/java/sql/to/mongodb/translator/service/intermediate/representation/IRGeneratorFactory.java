package sql.to.mongodb.translator.service.intermediate.representation;

import sql.to.mongodb.translator.service.exceptions.SQLParseException;
import sql.to.mongodb.translator.service.parser.Node;

/**
 * Фабрика для создания генераторов промежуточного представления
 */
public class IRGeneratorFactory {

    /**
     * Создание и выполнение генератора
     */
    public static SqlToMongoIR generateFromAST(Node astRoot) throws SQLParseException {
        if (astRoot == null) {
            throw new SQLParseException("AST root cannot be null");
        }

        SqlToMongoIRGenerator generator = new SqlToMongoIRGenerator(astRoot);
        return generator.generateIR();
    }

    /**
     * Получение сводной информации о запросе
     */
    public static String getQuerySummary(SqlToMongoIR ir) {
        if (ir == null) return "Empty IR";

        StringBuilder summary = new StringBuilder();
        summary.append("SQL to MongoDB Translation Summary:\n");
        summary.append("===================================\n");
        summary.append("Main Collection: ").append(ir.getMainCollection()).append("\n");
        summary.append("Has Joins: ").append(ir.isHasJoins()).append("\n");
        summary.append("Has Subqueries: ").append(ir.isHasSubqueries()).append("\n");
        summary.append("Has Aggregations: ").append(ir.isRequiresAggregation()).append("\n");
        summary.append("Has Group By: ").append(ir.isHasGroupBy()).append("\n");
        summary.append("Has Having: ").append(ir.isHasHaving()).append("\n");
        summary.append("Projection Fields: ").append(ir.getProjectionFields().size()).append("\n");
        summary.append("Where Conditions: ").append(ir.getWhereConditions().size()).append("\n");

        if (ir.getLimit() != null) {
            summary.append("Limit: ").append(ir.getLimit()).append("\n");
        }

        if (ir.getOffset() != null) {
            summary.append("Offset: ").append(ir.getOffset()).append("\n");
        }

        summary.append("===================================\n");

        return summary.toString();
    }
}
