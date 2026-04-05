package sql.to.mongodb.translator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.SortField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.expression.BinaryOperation;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinTable;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IRGeneratorTest {

    @Autowired
    IRGenerator irGenerator;

    @Autowired
    Scanner scanner;
    @Autowired
    Parser parser;

    private static List<Token> tokens = new ArrayList<>();

    @BeforeEach
    public void initialize() {
        tokens = new ArrayList<>();
    }


    @Test
    void testGenerationOfSimpleQuery() {
        SqlToMongoIR expectedIR = new SqlToMongoIR();
        expectedIR.setMainCollection("t");
        expectedIR.getProjectionFields().add(new ProjectionField(null, "*", null));

        String codeToScan = "SELECT * FROM t";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Node root = Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));

        SqlToMongoIR actualIR = irGenerator.generateIR(root);

        assertThat(actualIR)
                .usingRecursiveComparison()
                .isEqualTo(expectedIR);
    }

    @Test
    void testGenerationOfSimpleJoins() {
        SqlToMongoIR expectedIR = new SqlToMongoIR();
        expectedIR.setMainCollection("t1");
        expectedIR.getProjectionFields().add(new ProjectionField(null, "*", null));
        expectedIR.setHasJoins(true);
        expectedIR.getJoins().addAll(List.of(
                new JoinInfo(JoinInfo.JoinType.INNER,
                        new JoinTable("t1", null),
                        new JoinTable("t2", null),
                        new Comparison(new Field("t1", "id"),
                                "=",
                                new Field("t2", "id"))
                ),
                new JoinInfo(JoinInfo.JoinType.INNER,
                        new JoinTable("t2", null),
                        new JoinTable("t3", null),
                        new Comparison(new Field("t2", "id"),
                                "=",
                                new Field("t3", "id"))
                ))
        );

        String codeToScan = """
                  SELECT *\s
                  FROM t1 JOIN t2 ON t1.id = t2.id
                      JOIN t3 ON t2.id = t3.id
                """;

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Node root = Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));

        SqlToMongoIR actualIR = irGenerator.generateIR(root);

        assertThat(actualIR)
                .usingRecursiveComparison()
                .isEqualTo(expectedIR);
    }

    @Test
    void testGenerationOfOrderBy() {
        SqlToMongoIR expectedIR = new SqlToMongoIR();
        expectedIR.setMainCollection("Race");
        expectedIR.getProjectionFields().addAll(List.of(
                new ProjectionField("R", "*", null),
                new ArithmeticProjection(new BinaryOperation(
                        new Field("R", "TicketPrice"),
                        new Field("R", "SoldTickets"),
                        BinaryOperation.Operator.MULTIPLY),
                        "Profit"))
        );
        expectedIR.setHasComplexProjections(true);
        expectedIR.getAliases().put("R", "Race");
        expectedIR.getOrderBy().add(new SortField("R",
                "TicketPrice",
                SortField.SortDirection.DESC));

        String codeToScan = """
                SELECT R.*, R.TicketPrice * R.SoldTickets AS Profit
                FROM Race R
                ORDER BY R.TicketPrice DESC
                """;

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Node root = Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));

        SqlToMongoIR actualIR = irGenerator.generateIR(root);

        assertThat(actualIR)
                .usingRecursiveComparison()
                .isEqualTo(expectedIR);
    }
}
