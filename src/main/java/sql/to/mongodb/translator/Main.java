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
                SELECT Tm.Id_team, Tm.TeamName, Cn.CountryName
                                 FROM Staff S JOIN TeamStaff TS
                                 	ON Id_staff = TS.Staff
                                 	JOIN Team Tm
                                 	ON TS.Team = Tm.Id_team
                                 	JOIN Country Cn
                                 	ON Tm.Country = Cn.Id_country
                                 WHERE S.Post = 'Руководитель'
                                 	AND (TS.ExitDate IS NULL OR 1 + 2 + TS.ExitDate <= TS.ExitDate)
                                 GROUP BY Tm.Id_team, Tm.TeamName, Cn.CountryName
                                 HAVING COUNT(*) = 1""", tokens, errors);

        System.out.println("\nResult of scanner:\n");
        System.out.println(tokens);

        System.out.println("\nResult of parser:\n");

        Parser parser = new Parser(tokens, errors);
        Node parserRes = parser.tryAnalyse(false);
        System.out.println(parserRes);

    }
}