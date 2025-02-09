package sql.to.mongodb.translator;

import sql.to.mongodb.translator.parser.Node;
import sql.to.mongodb.translator.scanner.Scanner;
import sql.to.mongodb.translator.scanner.Token;
import sql.to.mongodb.translator.parser.Parser;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner();
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        scanner.tryAnalyse("""
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

        System.out.println("\nResult of scanner:\n");
        System.out.println(tokens);

        System.out.println("\nResult of parser:\n");

        Parser parser = new Parser(tokens, errors);
        Node parserRes = parser.tryAnalyse();
        System.out.println(parserRes);

    }
}