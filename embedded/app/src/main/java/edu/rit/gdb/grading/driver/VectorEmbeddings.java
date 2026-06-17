package edu.rit.gdb.grading.driver;

import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.AuthTokens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.observation.Observation;
import org.springframework.ai.ollama.OllamaEmbeddingModel;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.core.io.ClassPathResource;

import io.micrometer.observation.ObservationRegistry;

import org.neo4j.driver.GraphDatabase;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Driver;

public class VectorEmbeddings {


    public  static TransformersEmbeddingModel initializeEmbeddingModel(){

        TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();

        embeddingModel.setTokenizerResource(new ClassPathResource("/onnx/tokenizer.json"));
        embeddingModel.setModelResource(new ClassPathResource("/onnx/model.onnx"));

        // (optional) defaults to ${java.io.tmpdir}/spring-ai-onnx-model
        // Only the http/https resources are cached by default.
        embeddingModel.setResourceCacheDirectory("/tmp/onnx-zoo");

        // (optional) Set the tokenizer padding if you see an errors like:
        // "ai.onnxruntime.OrtException: Supplied array is ragged, ..."
        embeddingModel.setTokenizerOptions(Map.of("padding", "true"));

        try {
            embeddingModel.afterPropertiesSet();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return embeddingModel;
        

    }
    

    //load dataset into neo4j
    public static void createEmbeddings(String dbUri,String dbUser,String dbPassword){
        TransformersEmbeddingModel embeddingModel = initializeEmbeddingModel();
        try ( var driver = GraphDatabase.driver( dbUri, AuthTokens.basic(dbUser, dbPassword))) {
                driver.verifyConnectivity();
                System.out.println("Connection established.");
                
                var result = driver.executableQuery("""
                    MATCH (p:Player) RETURN  p.name AS name,p.position AS position
                """)
                .withConfig(QueryConfig.builder().withDatabase("neo4j").build())
                .execute();

                var insertEmbedding = 
                    "UNWIND $players AS player\n" + 
                    "MATCH (m:Player {name: player.name, position: player.position})\n" + 
                    "SET m.embedding = player.embedding";
                

                List<Map<String, Object>> players_with_embeddings = new ArrayList<>();
                var records = result.records(); 

                int batchsize = 10;
                int batch_n = 1;
                records.forEach((r->{
                    String name = r.get("name").asString();
                    String position = r.get("position").asString();
                    Map<String, Object> map = Map.of(
                        "name", name,
                        "position", position,
                        "embedding", embeddingModel.embed(name+"\n" + "position" + position)
                    );
                    players_with_embeddings.add(map);

                    if (players_with_embeddings.size() >= batchsize){
                        System.out.println(players_with_embeddings.size());
                        var queryResult = driver.executableQuery(insertEmbedding)
                        .withParameters(Map.of("players",players_with_embeddings))
                        .withConfig(QueryConfig.builder().withDatabase("neo4j").build())
                        .execute();

                        var summary = queryResult.summary();
                        System.out.printf("CREATED RECORDS IN %d ms.%n",
                            summary.resultAvailableAfter(TimeUnit.MILLISECONDS));
                        players_with_embeddings.clear();
                        System.out.println(players_with_embeddings.size());
                    }
                    }));
                
            driver.executableQuery(insertEmbedding)
                        .withParameters(Map.of("players",players_with_embeddings))
                        .withConfig(QueryConfig.builder().withDatabase("neo4j").build())
                        .execute();

            players_with_embeddings.clear();
            }       

    }

    public static void searchPrompt(String dbUri,String dbUser,String dbPassword){
        TransformersEmbeddingModel embeddingModel = initializeEmbeddingModel();
        String queryPrompt = "Looking for a midfielder like Douglas Costa";
        float[] promptEmbedding = embeddingModel.embed(queryPrompt);
        try ( var driver = GraphDatabase.driver( dbUri, AuthTokens.basic(dbUser, dbPassword))) {
            driver.verifyConnectivity();
            System.out.println("Connection established.");

            var result = driver.executableQuery("""
                MATCH (p:Player)
                SEARCH p IN (
                    VECTOR INDEX players
                    FOR $queryEmbedding
                    LIMIT 5
                ) SCORE AS similarityScore
                RETURN p.name AS name, p.position AS position, similarityScore
                    """)
                        .withParameters(Map.of("queryEmbedding",promptEmbedding))
                        .withConfig(QueryConfig.builder().withDatabase("neo4j").build())
                        .execute();
            
            System.out.println("PLayers related to prompt: ");
            var records = result.records();
            records.forEach((r->{
                System.out.println(r);
            }));

            //output
            // Record<{name: "Martin Christensen", position: "Right Midfield", similarityScore: 0.8022669553756714}>
            // Record<{name: "Willian Costa", position: "Attacking Midfield", similarityScore: 0.8013665676116943}>
            // Record<{name: "Jared Jeffrey", position: "Central Midfield", similarityScore: 0.7905833721160889}>
            // Record<{name: "Douglas Costa", position: "Left Wing", similarityScore: 0.7902960777282715}>
            // Record<{name: "Mikhail Gashchenkov", position: "Left Midfield", similarityScore: 0.7747856378555298}>
        }

    }
    //
    public static void main(String[] args) {
         final String dbUri = "neo4j://127.0.0.1:7687";
         final String dbUser = "neo4j";
         final String dbPassword = "neo4j-database";

        // createEmbeddings(dbUri, dbUser, dbPassword);
        searchPrompt(dbUri, dbUser, dbPassword);

}
}
