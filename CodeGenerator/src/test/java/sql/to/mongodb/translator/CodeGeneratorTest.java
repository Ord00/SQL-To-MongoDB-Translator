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
                "db.collection.find({}, {column_name: 1})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT column_name FROM collection")
        );
        Assertions.assertEquals(
                "db.collection.find({}, {a: 1, b: 1, c: 1, d: 1})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT a, b, c, d FROM collection")
        );
    }

    @Test
    public void wherePartTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({age: {$gt: 22}})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE age > 22")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$lt: 'abcd'}})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE name < 'abcd'")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$eq: -11}})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE income = -11")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$ne: 'abcd'}})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE 'abcd' <> name")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$gt: -11}})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE -11 < income")
        );
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE income < age"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE -11 <> 22"));
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

        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection LIMIT 10 LIMIT 2"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection OFFSET 2 OFFSET 10"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection LIMIT '10'"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection OFFSET -2"));
    }

    @Test
    public void combinationTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({age: {$ne: 22}}, {column_name: 1})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT column_name FROM collection WHERE age <> 22")
        );
        Assertions.assertEquals(
                "db.collection.find({}, {a: 1, b: 1, c: 1, d: 1}).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT a, b, c, d FROM collection LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$lt: -11}}).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE income < -11 LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$eq: 'abcd'}}).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE name = 'abcd' LIMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({age: {$lt: 22}}, {column_name: 1}).limit(10)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT column_name FROM collection WHERE 22 > age LIMIT 10")
        );
        Assertions.assertEquals(
                "db.collection.find({income: {$ne: -11}}, {a: 1, b: 1, c: 1, d: 1}).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT a, b, c, d FROM collection WHERE -11 <> income OFFSET 2")
        );
    }

    @Test
    public void parsingTest() throws CodeGenerationException {
        Assertions.assertEquals(
                "db.collection.find({age: {$gt: 22}}, {a: 1, b: 1, c: 1, d: 1}).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("SeLeCt a, b, c, d fRoM collection whEre age > 22 liMIT 10 OFFSET 2")
        );
        Assertions.assertEquals(
                "db.collection.find({age: {$gt: 22}}, {a: 1, b: 1, c: 1, d: 1}).limit(10).skip(2)",
                rabbitMQTestHelper.getCodeGeneratorResult("   SELECT  a,b,   c  ,d    FROM  collection   " +
                        " WHERE age> 22   LIMIT 10 OFFSET  2    ")
        );
        Assertions.assertEquals(
                "db.c0lL__EcTi0n.find({n23_ame: {$gt: ''}}, {a1_A2_: 1, _b_B: 1, C13: 1, _Dd_1_23_: 1})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECT a1_A2_, _b_B, C13, _Dd_1_23_ FROM c0lL__EcTi0n WHERE n23_ame > ''")
        );
        Assertions.assertEquals(
                "db.collection.find({name: {$gt: 'a\\'b\\\\\\cd\\''}}, {a: 1, b: 1, c: 1, d: 1})",
                rabbitMQTestHelper.getCodeGeneratorResult("SELECt a, b, c, d FROM collection WHERE name > 'a\\'b\\\\\\cd\\''")
        );
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM 0_nameStartFromDigit"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE22 < age"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE age > 22LIMIT 10"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE name = 'ab'cd'"));
        Assertions.assertNull(rabbitMQTestHelper.getCodeGeneratorResult("SELECT * FROM collection WHERE 'abcd\\' = name"));
    }
}
