package sql.to.mongodb.translator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import sql.to.mongodb.translator.config.RabbitMQConfig;
import sql.to.mongodb.translator.exceptions.CodeGenerationException;
import sql.to.mongodb.translator.helper.RabbitMQTestHelper;
import sql.to.mongodb.translator.helper.TestContainersHelper;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(RabbitMQConfig.class)
public class CodeGeneratorTest {
    @Autowired
    private RabbitMQTestHelper rabbitMQTestHelper;

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

    @Test
    public void columnNamesTest() throws CodeGenerationException {

        Assertions.assertEquals(
                "db.collection.find({})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection")
        );
        Assertions.assertEquals(
                "db.collection.find({}, { column_name: 1 })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT column_name FROM collection")
        );
        Assertions.assertEquals(
                "db.collection.find({}, { a: 1, b: 1, c: 1, d: 1 })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT a, b, c, d FROM collection")
        );
    }

    @Test
    public void wherePartTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({ age: { $gt: 22.0 } })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE age > 22")
        );
        Assertions.assertEquals(
                "db.collection.find({ name: { $lt: 'abcd' } })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE name < 'abcd'")
        );
        Assertions.assertEquals(
                "db.collection.find({ income: -11.0 })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE income = -11")
        );
        Assertions.assertEquals(
                "db.collection.find({ name: { $ne: 'abcd' } })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE 'abcd' <> name")
        );
        Assertions.assertEquals(
                "db.collection.find({ income: { $gt: -11.0 } })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE -11 < income")
        );
        Assertions.assertEquals(
                "db.collection.find({ $expr: { $lt: [ \"$income\", \"$age\" ] } })",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE income < age")
        );
        Assertions.assertThrows(CodeGenerationException.class,
                () -> rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE -11 <> 22"));
    }

    @Test
    public void skipLimitPartTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({}).limit(10)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection LIMIT 10")
        );
        Assertions.assertEquals(
                "db.collection.find({}).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({}).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection LIMIT 10 OFFSET 2")
        );
    }

    @Test
    public void combinationTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({ age: { $ne: 22.0 } }, { column_name: 1 })",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECT column_name FROM collection WHERE age <> 22")
        );
        Assertions.assertEquals(
                "db.collection.find({}, { a: 1, b: 1, c: 1, d: 1 }).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECT a, b, c, d FROM collection LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({ income: { $lt: -11.0 } }).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECT * FROM collection WHERE income < -11 LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({ name: 'abcd' }).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECT * FROM collection WHERE name = 'abcd' LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({ age: { $lt: 22.0 } }, { column_name: 1 }).limit(10)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECT column_name FROM collection WHERE 22 > age LIMIT 10")
        );
        Assertions.assertEquals(
                "db.collection.find({ income: { $ne: -11.0 } }, { a: 1, b: 1, c: 1, d: 1 }).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECT a, b, c, d FROM collection WHERE -11 <> income OFFSET 2")
        );
    }

    @Test
    public void parsingTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({ age: { $gt: 22.0 } }, { a: 1, b: 1, c: 1, d: 1 }).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SeLeCt a, b, c, d fRoM collection whEre age > 22 liMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({ age: { $gt: 22.0 } }, { a: 1, b: 1, c: 1, d: 1 }).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        """
                                SELECT  a, b, c, d
                                FROM  collection
                                WHERE age > 22
                                LIMIT 10
                                OFFSET 2"""
                ));
        Assertions.assertEquals(
                "db.c0lL__EcTi0n.find({ n23_ame: { $gt: '' } }, { a1_A2_: 1, _b_B: 1, C13: 1, _Dd_1_23_: 1 })",
                rabbitMQTestHelper.getCodeGeneratorResult(
                        "SELECt a1_A2_, _b_B, C13, _Dd_1_23_ FROM c0lL__EcTi0n WHERE n23_ame > ''")
        );
    }

    @Test
    void testGenerationOfLogicalConditionAndIn() throws CodeGenerationException {

        String expectedMongoCode = """
                db.Race.aggregate([
                    {
                        $lookup: {
                            from: "StaffRace",
                            localField: "Id_race",
                            foreignField: "Race",
                            as: "staffRaceJoin"
                        }
                    },
                    {
                        $unwind: {
                            path: "$staffRaceJoin",
                            preserveNullAndEmptyArrays: true
                        }
                    },
                    {
                        $lookup: {
                            from: "Staff",
                            localField: "staffRaceJoin.Staff",
                            foreignField: "Id_staff",
                            as: "staffJoin"
                        }
                    },
                    {
                        $unwind: {
                            path: "$staffJoin",
                            preserveNullAndEmptyArrays: true
                        }
                    },
                    {
                        $lookup: {
                            from: "TeamStaff",
                            localField: "staffJoin.Id_staff",
                            foreignField: "Staff",
                            as: "teamStaffJoin"
                        }
                    },
                    {
                        $unwind: {
                            path: "$teamStaffJoin",
                            preserveNullAndEmptyArrays: true
                        }
                    },
                    {
                        $lookup: {
                            from: "Team",
                            localField: "teamStaffJoin.Team",
                            foreignField: "Id_team",
                            as: "teamJoin"
                        }
                    },
                    {
                        $unwind: {
                            path: "$teamJoin",
                            preserveNullAndEmptyArrays: true
                        }
                    },
                    {
                        $lookup: {
                            from: "Country",
                            localField: "teamJoin.Country",
                            foreignField: "Id_country",
                            as: "countryJoin"
                        }
                    },
                    {
                        $unwind: {
                            path: "$countryJoin",
                            preserveNullAndEmptyArrays: true
                        }
                    },
                    {
                        $match: {
                            $expr: {
                                $and: [
                                    { $gte: ["$RaceDate", "$teamStaffJoin.EntryDate"] },
                                    {
                                        $or: [
                                            { $eq: ["$teamStaffJoin.ExitDate", null] },
                                            { $lte: ["$RaceDate", "$teamStaffJoin.ExitDate"] }
                                        ]
                                    }
                                ]
                            }
                        }
                    },
                    {
                        $facet: {
                            "topProfits": [
                                {
                                    $project: {
                                        Profit: { $multiply: ["$TicketPrice", "$SoldTickets"] }
                                    }
                                },
                                { $sort: { Profit: -1 } },
                                { $limit: 3 },
                                { $group: { _id: null, profits: { $addToSet: "$Profit" } } }
                            ],
                            "data": [{ $match: {} }]
                        }
                    },
                    {
                        $unwind: "$topProfits"
                    },
                    {
                        $match: {
                            $expr: {
                                $in: [
                                    { $multiply: ["$TicketPrice", "$SoldTickets"] },
                                    "$topProfits.profits"
                                ]
                            }
                        }
                    },
                    {
                        $group: {
                            _id: {
                                Id_country: "$countryJoin.Id_country",
                                CountryName: "$countryJoin.CountryName"
                            }
                        }
                    },
                    {
                        $project: {
                            _id: 0,
                            Id_country: "$_id.Id_country",
                            CountryName: "$_id.CountryName"
                        }
                    }
                ])
                """;

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

        Assertions.assertEquals(expectedMongoCode,
                rabbitMQTestHelper.getCodeGeneratorResult(codeToScan));
    }
}
