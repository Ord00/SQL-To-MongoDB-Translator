package sql.to.mongodb.translator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sql.to.mongodb.translator.parser.Parser;
import sql.to.mongodb.translator.scanner.Scanner;
import sql.to.mongodb.translator.scanner.Token;

import java.util.ArrayList;
import java.util.List;

public class ParserTest {
    private static final Scanner SCANNER = new Scanner();
    private static List<Token> tokens = new ArrayList<>();
    private static List<String> errors = new ArrayList<>();

    @BeforeEach
    public void initialize() {
        tokens = new ArrayList<>();
        errors = new ArrayList<>();
    }

    @Test
    public void testInOfOneSubquery() {

        SCANNER.tryAnalyse("""
                SELECT id, name, file
                FROM products
                WHERE id IN (SELECT product_id
                             FROM sales)""", tokens, errors);

        Parser parser = new Parser(tokens, errors);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(false));
    }

    @Test
    public void testExistsAndJoin() {

        SCANNER.tryAnalyse("""
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
                				)""", tokens, errors);

        Parser parser = new Parser(tokens, errors);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(false));
    }

    @Test
    public void testAllWithSpecificTable() {

        SCANNER.tryAnalyse("""
                SELECT CompetitionName, Race.*
                FROM Competition LEFT JOIN Race
                	 ON Id_competition = Competition""", tokens, errors);

        Parser parser = new Parser(tokens, errors);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(false));
    }

    @Test
    public void testAllFunction() {

        SCANNER.tryAnalyse("""
                SELECT TP.Id_team, TP.TeamName
                FROM TeamProfit TP
                WHERE TP.Profit >= ALL(SELECT TP2.Profit
                                       FROM TeamProfit TP2)""", tokens, errors);

        Parser parser = new Parser(tokens, errors);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(false));
    }

    @Test
    public void testOrderBy() {

        SCANNER.tryAnalyse("""
                SELECT R.*, R.TicketPrice * R.SoldTickets AS Profit
                FROM Race R
                ORDER BY R.TicketPrice * R.SoldTickets DESC""", tokens, errors);

        Parser parser = new Parser(tokens, errors);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(false));
    }

    @Test
    public void testDistinct() {

        SCANNER.tryAnalyse("""
                SELECT DISTINCT Cn.*
                FROM Team Tm JOIN Country Cn
                   ON Tm.Country = Cn.Id_country
                WHERE EXISTS (SELECT 1
                             FROM Race R2 JOIN StaffRace SR2
                               ON R2.Id_race = SR2.Race
                               JOIN Staff S2
                               ON SR2.Staff = S2.Id_staff
                               JOIN TeamStaff TS2
                               ON S2.Id_staff = TS2.Staff
                               JOIN Team Tm2
                               ON TS2.Team = Tm2.Id_team
                             WHERE Cn.Id_country = Tm2.Country
                                 AND R2.RaceDate >= TS2.EntryDate
                                 AND (TS2.ExitDate IS NULL
                                     OR R2.RaceDate <= TS2.ExitDate)
                             GROUP BY Tm2.Id_team, Tm2.TeamName
                             HAVING COUNT(DISTINCT R2.Competition) = (SELECT COUNT(*)
                                                                      FROM Competition
                                                                     )	
                             )
                    AND NOT EXISTS (SELECT 1
                                FROM Race R3 JOIN StaffRace SR3
                                    ON R3.Id_race = SR3.Race
                                    JOIN Staff S3
                                    ON SR3.Staff = S3.Id_staff
                                    JOIN TeamStaff TS3
                                    ON S3.Id_staff = TS3.Staff
                                    JOIN Team Tm3
                                    ON TS3.Team = Tm3.Id_team
                                WHERE Tm.Id_team = Tm3.Id_team
                                )""", tokens, errors);

        Parser parser = new Parser(tokens, errors);
        Assertions.assertDoesNotThrow(() -> parser.tryAnalyse(false));
    }
}
