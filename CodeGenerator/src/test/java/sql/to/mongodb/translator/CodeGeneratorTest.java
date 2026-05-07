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
                db.Country.aggregate([
                    {
                        $lookup: {
                            from: "Team",
                            localField: "Id_country",
                            foreignField: "Country",
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
                            from: "TeamStaff",
                            localField: "teamJoin.Id_team",
                            foreignField: "Team",
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
                            from: "Staff",
                            localField: "teamStaffJoin.Staff",
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
                            from: "StaffRace",
                            localField: "staffJoin.Id_staff",
                            foreignField: "Staff",
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
                            from: "Race",
                            localField: "staffRaceJoin.Race",
                            foreignField: "Id_race",
                            as: "raceJoin"
                        }
                    },
                    {
                        $unwind: {
                            path: "$raceJoin",
                            preserveNullAndEmptyArrays: true
                        }
                    },
                    {
                        $match: {
                            $expr: {
                                $and: [
                                    { $gte: ["$raceJoin.RaceDate", "$teamStaffJoin.EntryDate"] },
                                    {
                                        $or: [
                                            { $eq: ["$teamStaffJoin.ExitDate", null] },
                                            { $lte: ["$raceJoin.RaceDate", "$teamStaffJoin.ExitDate"] }
                                        ]
                                    }
                                ]
                            }
                        }
                    },
                    {
                        $lookup: {
                            from: "Race",
                            pipeline: [
                                {
                                    $project: {
                                        Profit: { $multiply: ["$TicketPrice", "$SoldTickets"] }
                                    }
                                },
                                { $sort: { Profit: -1 } },
                                { $limit: 3 }
                            ],
                            as: "topProfits"
                        }
                    },
                    {
                        $addFields: {
                            topProfitsArray: {
                                $map: {
                                    input: "$topProfits",
                                    as: "p",
                                    in: "$$p.Profit"
                                }
                            }
                        }
                    },
                    {
                        $match: {
                            $expr: {
                                $in: [
                                    { $multiply: ["$raceJoin.TicketPrice", "$raceJoin.SoldTickets"] },
                                    "$topProfitsArray"
                                ]
                            }
                        }
                    },
                    {
                        $group: {
                            _id: {
                                Id_country: "$Id_country",
                                CountryName: "$CountryName"
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
                	AND R.TicketPrice * R.SoldTickets IN (SELECT R2.TicketPrice * R2.SoldTickets AS Profit
                										  FROM Race R2
                										  ORDER BY Profit DESC
                										  LIMIT 3)""";

        Assertions.assertEquals(expectedMongoCode,
                rabbitMQTestHelper.getCodeGeneratorResult(codeToScan));
    }

    @Test
    public void testExistsAndJoin() throws CodeGenerationException {
            String expectedMongoCode = """
                    db.Team.aggregate([
                        {
                            $lookup: {
                                from: "Competition",
                                pipeline: [],
                                as: "allCompetitions"
                            }
                        },
                        {
                            $lookup: {
                                from: "Race",
                                let: { teamId: "$Id_team" },
                                pipeline: [
                                    {
                                        $lookup: {
                                            from: "StaffRace",
                                            localField: "Id_race",
                                            foreignField: "Race",
                                            as: "sr"
                                        }
                                    },
                                    { $unwind: "$sr" },
                                    {
                                        $lookup: {
                                            from: "Staff",
                                            localField: "sr.Staff",
                                            foreignField: "Id_staff",
                                            as: "s"
                                        }
                                    },
                                    { $unwind: "$s" },
                                    {
                                        $lookup: {
                                            from: "TeamStaff",
                                            localField: "s.Id_staff",
                                            foreignField: "Staff",
                                            as: "ts"
                                        }
                                    },
                                    { $unwind: "$ts" },
                                    {
                                        $match: {
                                            $expr: {
                                                $and: [
                                                    { $eq: ["$ts.Team", "$$teamId"] },
                                                    { $gte: ["$RaceDate", "$ts.EntryDate"] },
                                                    {
                                                        $or: [
                                                            { $eq: ["$ts.ExitDate", null] },
                                                            { $lte: ["$RaceDate", "$ts.ExitDate"] }
                                                        ]
                                                    }
                                                ]
                                            }
                                        }
                                    },
                                    {
                                        $project: {
                                            Competition: 1
                                        }
                                    }
                                ],
                                as: "teamCompetitions"
                            }
                        },
                        {
                            $addFields: {
                                teamCompIds: {
                                    $setUnion: ["$teamCompetitions.Competition", []]
                                },
                                allCompIds: {
                                    $setUnion: ["$allCompetitions.Id_competition", []]
                                }
                            }
                        },
                        {
                            $match: {
                                $expr: {
                                    $eq: [
                                        { $size: "$teamCompIds" },
                                        { $size: "$allCompIds" }
                                    ]
                                }
                            }
                        },
                        {
                            $project: {
                                _id: 0,
                                TeamName: 1
                            }
                        }
                    ])
                    """;

            String codeToScan = """
                    SELECT Tm.TeamName
                    FROM Team Tm
                    WHERE NOT EXISTS
                              (SELECT 1
                              FROM Competition Comp2
                              WHERE NOT EXISTS
                                        (SELECT 1
                                        FROM Race R3 JOIN StaffRace SR3
                                        ON R3.Id_race = SR3.Race
                                        JOIN Staff S3
                                        ON SR3.Staff = S3.Id_staff
                                        JOIN TeamStaff TS3
                                        ON S3.Id_staff = TS3.Staff
                                        JOIN Team Tm3
                                        ON TS3.Team = Tm3.Id_team
                                        WHERE R3.RaceDate >= TS3.EntryDate
                                                AND (TS3.ExitDate IS NULL OR R3.RaceDate <= TS3.ExitDate)
                                                AND Tm.Id_team = Tm3.Id_team
                                                AND Comp2.Id_competition = R3.Competition
                                        )
                              )""";

        Assertions.assertEquals(expectedMongoCode,
                rabbitMQTestHelper.getCodeGeneratorResult(codeToScan));
    }

    @Test
    public void testExistsAndGroupBy() throws CodeGenerationException {
        String expectedMongoCode = """
                db.Competition.aggregate([
                      {
                          $lookup: {
                              from: "Race",
                              let: { compId: "$Id_competition" },
                              pipeline: [
                                  {
                                      $match: {
                                          $expr: {
                                              $eq: ["$Competition", "$$compId"]
                                          }
                                      }
                                  },
                                  {
                                      $lookup: {
                                          from: "StaffRace",
                                          localField: "Id_race",
                                          foreignField: "Race",
                                          as: "sr"
                                      }
                                  },
                                  {
                                      $unwind: {
                                          path: "$sr",
                                          preserveNullAndEmptyArrays: true
                                      }
                                  },
                                  {
                                      $lookup: {
                                          from: "Staff",
                                          localField: "sr.Staff",
                                          foreignField: "Id_staff",
                                          as: "s"
                                      }
                                  },
                                  {
                                      $unwind: {
                                          path: "$s",
                                          preserveNullAndEmptyArrays: true
                                      }
                                  },
                                  {
                                      $lookup: {
                                          from: "TeamStaff",
                                          localField: "s.Id_staff",
                                          foreignField: "Staff",
                                          as: "ts"
                                      }
                                  },
                                  {
                                      $unwind: {
                                          path: "$ts",
                                          preserveNullAndEmptyArrays: true
                                      }
                                  },
                                  {
                                      $match: {
                                          $expr: {
                                              $and: [
                                                  { $eq: ["$Competition", "$$compId"] },
                                                  { $gte: ["$RaceDate", "$ts.EntryDate"] },
                                                  {
                                                      $or: [
                                                          { $eq: ["$ts.ExitDate", null] },
                                                          { $lte: ["$RaceDate", "$ts.ExitDate"] }
                                                      ]
                                                  }
                                              ]
                                          }
                                      }
                                  },
                                  {
                                      $lookup: {
                                          from: "Team",
                                          localField: "ts.Team",
                                          foreignField: "Id_team",
                                          as: "tm"
                                      }
                                  },
                                  {
                                      $unwind: {
                                          path: "$tm",
                                          preserveNullAndEmptyArrays: true
                                      }
                                  },
                                  {
                                      $lookup: {
                                          from: "Country",
                                          localField: "tm.Country",
                                          foreignField: "Id_country",
                                          as: "cn"
                                      }
                                  },
                                  {
                                      $unwind: {
                                          path: "$cn",
                                          preserveNullAndEmptyArrays: true
                                      }
                                  },
                                  {
                                      $group: {
                                          _id: "$cn.Id_country",
                                          teams: {
                                              $addToSet: "$tm.Id_team"
                                          }
                                      }
                                  },
                                  {
                                      $project: {
                                          teams: {
                                              $setDifference: ["$teams", [null]]
                                          }
                                      }
                                  },
                                  {
                                      $project: {
                                          teamCount: {
                                              $size: "$teams"
                                          }
                                      }
                                  },
                                  {
                                      $match: {
                                          teamCount: { $lt: 2 }
                                      }
                                  }
                              ],
                              as: "invalidCountries"
                          }
                      },
                      {
                          $match: {
                              $expr: {
                                  $eq: [
                                      { $size: "$invalidCountries" },
                                      0
                                  ]
                              }
                          }
                      },
                      {
                          $project: {
                              _id: 0,
                              Id_competition: 1,
                              CompetitionName: 1
                          }
                      }
                ])
                """;

        String codeToScan = """
                SELECT DISTINCT Comp.Id_competition, Comp.CompetitionName
                FROM Competition Comp JOIN Race R
                 ON R.Competition = Comp.Id_competition
                WHERE NOT EXISTS(SELECT 1
                			 FROM Race R2 LEFT JOIN StaffRace SR2
                			 	 ON R2.Id_race = SR2.Race
                				 LEFT JOIN Staff S2
                				 ON SR2.Staff = S2.Id_staff
                				 LEFT JOIN TeamStaff TS2
                				 ON S2.Id_staff = TS2.Staff
                				 LEFT JOIN Team Tm2
                				 ON TS2.Team = Tm2.Id_team
                				 LEFT JOIN Country Cn2
                				 ON Tm2.Country = Cn2.Id_country
                			 WHERE R2.Competition = Comp.Id_competition
                				 AND R2.RaceDate >= TS2.EntryDate
                				 AND (TS2.ExitDate IS NULL OR R2.RaceDate <= TS2.ExitDate)
                			 GROUP BY Cn2.Id_country
                			 HAVING COUNT(DISTINCT Tm2.Id_team) < 2
                			 )""";

        Assertions.assertEquals(expectedMongoCode,
                rabbitMQTestHelper.getCodeGeneratorResult(codeToScan));
    }
}
