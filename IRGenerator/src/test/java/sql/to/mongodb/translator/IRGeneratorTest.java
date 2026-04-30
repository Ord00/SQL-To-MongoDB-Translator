package sql.to.mongodb.translator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import sql.to.mongodb.translator.config.RabbitMQConfig;
import sql.to.mongodb.translator.helper.RabbitMQTestHelper;
import sql.to.mongodb.translator.helper.TestContainersHelper;
import sql.to.mongodb.translator.ir.Field;
import sql.to.mongodb.translator.ir.SortField;
import sql.to.mongodb.translator.ir.SqlToMongoIR;
import sql.to.mongodb.translator.ir.Subquery;
import sql.to.mongodb.translator.ir.condition.Comparison;
import sql.to.mongodb.translator.ir.condition.InCondition;
import sql.to.mongodb.translator.ir.condition.LinkNode;
import sql.to.mongodb.translator.ir.condition.NullCheck;
import sql.to.mongodb.translator.ir.expression.BinaryOperation;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.join.JoinTable;
import sql.to.mongodb.translator.ir.projection.ArithmeticProjection;
import sql.to.mongodb.translator.ir.projection.ProjectionField;
import sql.to.mongodb.translator.parser.Node;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(RabbitMQConfig.class)
class IRGeneratorTest {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainersHelper.properties(registry);
    }

    @BeforeAll
    static void startContainers() {
        TestContainersHelper.startContainers();
    }

    @AfterAll
    static void stopContainers() {
        TestContainersHelper.stopContainers();
    }

    @Autowired
    IRGenerator irGenerator;

    @Autowired
    private RabbitMQTestHelper rabbitMQTestHelper;

    @Test
    void testGenerationOfSimpleQuery() {
        SqlToMongoIR expectedIR = new SqlToMongoIR();
        expectedIR.setMainCollection("t");
        expectedIR.getProjectionFields().add(new ProjectionField(null, "*", null));

        String codeToScan = "SELECT * FROM t";

        Node root = rabbitMQTestHelper.getParserResult(codeToScan);
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

        Node root = rabbitMQTestHelper.getParserResult(codeToScan);
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

        Node root = rabbitMQTestHelper.getParserResult(codeToScan);
        SqlToMongoIR actualIR = irGenerator.generateIR(root);

        assertThat(actualIR)
                .usingRecursiveComparison()
                .isEqualTo(expectedIR);
    }

    @Test
    void testGenerationOfLogicalConditionAndIn() {
        SqlToMongoIR expectedIR = new SqlToMongoIR();
        expectedIR.setMainCollection("Race");
        expectedIR.setDistinct(true);
        expectedIR.getProjectionFields().addAll(List.of(
                new ProjectionField("Cn", "Id_country", null),
                new ProjectionField("Cn", "CountryName", null)
        ));
        expectedIR.getAliases().putAll(Map.of(
                "R", "Race",
                "SR", "StaffRace",
                "S", "Staff",
                "TS", "TeamStaff",
                "Tm", "Team",
                "Cn", "Country"
        ));

        expectedIR.setJoins(List.of(
                new JoinInfo(JoinInfo.JoinType.RIGHT,
                        new JoinTable("Race", "R"),
                        new JoinTable("StaffRace", "SR"),
                        new Comparison(new Field("R", "Id_race"),
                                "=",
                                new Field("SR", "Race"))
                ),
                new JoinInfo(JoinInfo.JoinType.RIGHT,
                        new JoinTable("StaffRace", "SR"),
                        new JoinTable("Staff", "S"),
                        new Comparison(new Field("SR", "Staff"),
                                "=",
                                new Field("S", "Id_staff"))
                ),
                new JoinInfo(JoinInfo.JoinType.RIGHT,
                        new JoinTable("Staff", "S"),
                        new JoinTable("TeamStaff", "TS"),
                        new Comparison(new Field("S", "Id_staff"),
                                "=",
                                new Field("TS", "Staff"))
                ),
                new JoinInfo(JoinInfo.JoinType.RIGHT,
                        new JoinTable("TeamStaff", "TS"),
                        new JoinTable("Team", "Tm"),
                        new Comparison(new Field("TS", "Team"),
                                "=",
                                new Field("Tm", "Id_team"))
                ),
                new JoinInfo(JoinInfo.JoinType.RIGHT,
                        new JoinTable("Team", "Tm"),
                        new JoinTable("Country", "Cn"),
                        new Comparison(new Field("Tm", "Country"),
                                "=",
                                new Field("Cn", "Id_country"))
                )
        ));
        expectedIR.setHasJoins(true);

        SqlToMongoIR expectedSubIR = new SqlToMongoIR();
        expectedSubIR.setMainCollection("Race");
        expectedSubIR.getAliases().put("R", "Race");
        expectedSubIR.getProjectionFields().add(new ArithmeticProjection(new BinaryOperation(
                new Field("R", "TicketPrice"),
                new Field("R", "SoldTickets"),
                BinaryOperation.Operator.MULTIPLY),
                "Profit"));
        expectedSubIR.setHasComplexProjections(true);
        expectedSubIR.getOrderBy().add(new SortField(
                "Profit",
                null,
                SortField.SortDirection.DESC));
        expectedSubIR.setLimit(3);


        expectedIR.setWhereCondition(new LinkNode(LinkNode.LinkType.AND, List.of(
                new LinkNode(LinkNode.LinkType.AND, List.of(
                        new Comparison(
                                new Field("R", "RaceDate"),
                                ">=",
                                new Field("TS", "EntryDate")),
                        new LinkNode(LinkNode.LinkType.OR, List.of(
                                new NullCheck(new Field("TS", "ExitDate"), true),
                                new Comparison(
                                        new Field("R", "RaceDate"),
                                        "<=",
                                        new Field("TS", "ExitDate"))
                        ))
                )),
                new InCondition(
                        new BinaryOperation(new Field("R", "TicketPrice"),
                                new Field("R", "SoldTickets"),
                                BinaryOperation.Operator.MULTIPLY),
                        List.of(new Subquery(expectedSubIR))
                )
        )));

        String codeToScan = """
                SELECT DISTINCT Cn.Id_country, Cn.CountryName
                FROM Race R RIGHT JOIN StaffRace SR
                	ON R.Id_race = SR.Race
                	RIGHT JOIN Staff S
                	ON SR.Staff = S.Id_staff
                	RIGHT JOIN TeamStaff TS
                	ON S.Id_staff = TS.Staff
                	RIGHT JOIN Team Tm
                	ON TS.Team = Tm.Id_team
                	RIGHT JOIN Country Cn
                	ON Tm.Country = Cn.Id_country
                WHERE R.RaceDate >= TS.EntryDate
                	AND (TS.ExitDate IS NULL OR R.RaceDate <= TS.ExitDate)
                	AND R.TicketPrice * R.SoldTickets IN (SELECT R.TicketPrice * R.SoldTickets AS Profit
                										  FROM Race R
                										  ORDER BY Profit DESC
                										  LIMIT 3)""";

        Node root = rabbitMQTestHelper.getParserResult(codeToScan);
        SqlToMongoIR actualIR = irGenerator.generateIR(root);

        assertThat(actualIR)
                .usingRecursiveComparison()
                .isEqualTo(expectedIR);
    }
}
