package sql.to.mongodb.translator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import exceptions.SQLParseException;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class ParserTest {

    @Autowired
    private Scanner scanner = new Scanner();

    @Autowired
    private Parser parser;

    private static List<Token> tokens = new ArrayList<>();

    @BeforeEach
    public void initialize() {
        tokens = new ArrayList<>();
    }

    @Test
    public void testInOfOneSubquery() {
        String codeToScan = """
                SELECT id, name, file
                FROM products
                WHERE id IN (SELECT product_id
                             FROM sales)""";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testExistsAndJoin() {
        String codeToScan = """
                SELECT Tm.TeamName
                FROM Team Tm
                WHERE NOT EXISTS (SELECT 1
                				  FROM Competition Comp2
                				  WHERE (SELECT 1
                                        FROM Race R3 JOIN StaffRace SR3
                                        ON R3.Id_race = SR3.Race
                                        JOIN Staff S3
                                        ON SR3.Staff = S3.Id_staff
                                        JOIN TeamStaff TS3
                                        ON S3.Id_staff = TS3.Staff
                                        JOIN Team Tm3
                                        ON TS3.Team = Tm3.Id_team
                                        WHERE RaceDate >= EntryDate
                                                AND (TS3.ExitDate IS NULL OR R3.RaceDate <= TS3.ExitDate)
                                                AND Tm.Id_team = Tm3.Id_team
                                                AND Comp2.Id_competition = R3.Competition
                                        ) >= 1
                				)""";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testAllWithSpecificTable() {
        String codeToScan = """
                SELECT CompetitionName, Race.*
                FROM Competition LEFT JOIN Race
                	 ON Id_competition = Competition""";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testAllFunction() {
        String codeToScan = """
                SELECT TP.Id_team, TP.TeamName
                FROM TeamProfit TP
                WHERE TP.Profit >= ALL(SELECT TP2.Profit
                                       FROM TeamProfit TP2)""";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testOrderBy() {
        String codeToScan = """
                SELECT R.*, R.TicketPrice * R.SoldTickets AS Profit
                FROM Race R
                ORDER BY R.TicketPrice * R.SoldTickets DESC""";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }

    @Test
    public void testLimit() throws SQLParseException {
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

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));

        System.out.println(parser.tryAnalyse(tokens));
    }

    @Test
    public void testCaseAsAggregateAttribute() {

        String codeToScan = """
                SELECT (CalcRes.ChampionshipNum * 100) / CalcRes.Total AS Championship,
                       (CalcRes.CupNum * 100) / CalcRes.Total AS Cup,
                       (CalcRes.PrecedenceNum * 100) / CalcRes.Total AS Precedence
                FROM (SELECT SUM(CASE WHEN CT.CompetitionTypeName = 'Чемпионат'\s
                                      THEN 1\s
                                      ELSE 0\s
                                      END) ChampionshipNum,
                             SUM(CASE WHEN CT.CompetitionTypeName = 'Кубок'\s
                                      THEN 1\s
                                      ELSE 0\s
                                      END) CupNum,
                             SUM(CASE WHEN CT.CompetitionTypeName = 'Первенство'\s
                                      THEN 1\s
                                      ELSE 0\s
                                      END) PrecedenceNum
                      FROM Competition Comp JOIN CompetitionType CT
                        ON Comp.CompetitionType = CT.Id_competition_type
                     ) AS CalcRes""";

        Assertions.assertDoesNotThrow(() -> scanner.tryAnalyse(codeToScan, tokens));

        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(tokens));
    }
}
