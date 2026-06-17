package edu.rit.gdb.grading.driver;

import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.AuthTokens;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Driver;

public class CommunityEdition {
    

    public static void addPersonNode(Driver driver){
        var result = driver.executableQuery("""
            CREATE (a:Person {name: $name})
            CREATE (b:Person {name: $friendName})
            CREATE (a)-[:KNOWS]->(b)
            """)
            .withParameters(Map.of("name", "Alice", "friendName", "David"))
            .withConfig(QueryConfig.builder().withDatabase("neo4j").build())
            .execute();

        var summary = result.summary();
        System.out.printf("CREATED %d RECORDS IN %d ms.%n",
            summary.counters().nodesCreated(),
            summary.resultAvailableAfter(TimeUnit.MILLISECONDS));

    }
    public static void deletePersonNode(Driver driver){
        var result = driver.executableQuery("""
                MATCH (n:Person {name: $name})
                DETACH DELETE n
                """)
                .withParameters(Map.of("name","Alice"))
                .withConfig(QueryConfig.builder()
                .withDatabase("neo4j").build())
            .execute();
        var summary = result.summary();
        System.out.println("Query updated the database?");
        System.out.println(summary.counters().containsUpdates());    
    }
    public static void main(String[] args) {
         final String dbUri = "bolt://localhost:7687";
         final String dbUser = "neo4j";
         final String dbPassword = "rit_neo4j";
        final String action = args[0];

         try ( var driver = GraphDatabase.driver( dbUri, AuthTokens.basic(dbUser, dbPassword))) {
            driver.verifyConnectivity();
            System.out.println("Connection established.");
            
            if(action.equals("add")){
                addPersonNode(driver);
            }else{
                deletePersonNode(driver);
            }


            }
        }
}
