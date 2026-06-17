package edu.rit.gdb.grading;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import apoc.ApocConfig;
import apoc.export.graphml.ExportGraphML;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import io.micrometer.observation.ObservationRegistry;

public class EmbeddingsEmbedded {


	public  static OllamaEmbeddingModel initializeEmbeddingModel(){

        var ollamaApi = OllamaApi.builder().build();

       var embeddingModel = new OllamaEmbeddingModel(ollamaApi,
        OllamaEmbeddingOptions.builder()
			.model(OllamaModel.NOMIC_EMBED_TEXT)
            .build(),
            ObservationRegistry.NOOP,
    		ModelManagementOptions.defaults()
        );
        return embeddingModel;
    }

	public static OpenAiEmbeddingModel initializeOpenAIModel(){
		var openAiApi = OpenAiApi.builder()
          .apiKey(System.getenv("OPENAI_API_KEY"))
          .build();

		var embeddingModel = new OpenAiEmbeddingModel(
		openAiApi,
        MetadataMode.EMBED,
        OpenAiEmbeddingOptions.builder()
                .model("text-embedding-ada-002")
                .build());
		return embeddingModel;

	}
	public static void exportGraphML(String neo4jFolder,String database,String fileName){

		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			
			DependencyResolver resolver = ((GraphDatabaseAPI) db).getDependencyResolver();
			GlobalProcedures procedures = resolver.resolveDependency(
					GlobalProcedures.class, DependencyResolver.SelectionStrategy.SINGLE);
			
			procedures.registerProcedure(ExportGraphML.class);
			
			ApocConfig.apocConfig().setProperty(ApocConfig.APOC_IMPORT_FILE_ENABLED, true);
			ApocConfig.apocConfig().setProperty(ApocConfig.APOC_EXPORT_FILE_ENABLED, true);

			String query = """
					MATCH (p:Player) RETURN  p 
			""";

		
			Result res = null;
			try ( Transaction tx = db.beginTx();){
				res = tx.execute("CALL apoc.export.graphml.query(\""+query+"\",\""+fileName+"\", {readLabels: true, storeNodeIds: true, useTypes: true})");
				System.out.println(res.resultAsString());
				tx.commit();
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}
		
		
	}

	public static void embeddings(String neo4jFolder,String database){
		OllamaEmbeddingModel embeddingModel = initializeEmbeddingModel();
		
		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			
			
			// This is only for RETURN-type queries, that is, you want to retrieve something.
			String query = "MATCH (p:Player) RETURN  p";
			
			var insertEmbedding = 
                    "UNWIND $players AS player\n" + 
                    "MATCH (m:Player {name: player.name, position: player.position})\n" + 
            		"SET m.embedding = player.embedding";

			List<Map<String, Object>> players_with_embeddings = new ArrayList<>();
			try ( Transaction tx = db.beginTx()){
				try (Result result = tx.execute(query)){
					while(result.hasNext()){
						Map<String, Object> row = result.next();
						Node player = (Node) row.get("p");
						if (player.hasProperty("name") && player.hasProperty("position")) {
							String name = (String) player.getProperty("name");
							String position = (String) player.getProperty("position");
							Map<String, Object> map = Map.of(
							"name", name,
							"position", position,
							"embedding", embeddingModel.embed(name+"\n" + "position" + position)
							);
							players_with_embeddings.add(map);
            			}			
					}

				}catch(Exception oops){
					System.out.println("Dang it!");
					oops.printStackTrace();
				}
				
				Result result2 = tx.execute(insertEmbedding,Map.of("players",players_with_embeddings));
				System.out.println(result2.resultAsString());

				String query3 = """
						MATCH (a:Player {name: "Alessandro Budel"})
						MATCH (b:Player {name: "Aleksandr Samedov"})
						RETURN vector.similarity.cosine(a.embedding, b.embedding)
						""";
				Result vectorSimiliarity = tx.execute(query3);

				while(vectorSimiliarity.hasNext()){
					Map<String, Object> row = vectorSimiliarity.next();
					System.out.println(row);
				}
				
				tx.commit();
				
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.println("Dang it!");
			oops.printStackTrace();
		}
	}

	public static void createVectorIndex(String neo4jFolder,String database){
		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			

			String query = """
					CREATE VECTOR INDEX playerIndex
						FOR (m:Player)
						ON m.embedding
						OPTIONS {indexConfig: {
							`vector.dimensions`: 768,
							`vector.similarity_function`: 'cosine'
						}}
			""";
			
			try ( Transaction tx = db.beginTx();){
				Result index = tx.execute(query);
				System.out.println(index.resultAsString());
				while(index.hasNext()){
					Map<String, Object> row = index.next();
					System.out.println(row);
				}
				tx.commit();
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}
		
	}

	public static void loadPlayerData(String neo4jFolder,String database){
		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			

			String query = """
					
			LOAD CSV WITH HEADERS FROM 'https://s3-eu-west-1.amazonaws.com/football-transfers.neo4j.com/transfers-all.csv' AS row
			WITH row LIMIT 25	
			MERGE (player:Player {id: row.playerUri})
				ON CREATE SET player.name =  row.playerName, player.position = row.playerPosition;
			""";
			
			try ( Transaction tx = db.beginTx();){
				Result result = tx.execute(query);
				System.out.println(result.resultAsString());
				tx.commit();
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}
	}

	public static void findSimilarPlayers(String neo4jFolder,String database){

		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			

			String query = """
				CYPHER 25 MATCH (sourcePlayer:Player {name: 'Alessandro Budel'})
				MATCH (matchingPlayer:Player)
				SEARCH matchingPlayer IN (
					VECTOR INDEX playerIndex
					FOR sourcePlayer.embedding
					LIMIT 5
				) SCORE AS similarityScore
				RETURN matchingPlayer.name AS name, 
					matchingPlayer.position AS position, 
					similarityScore    
			""";
			try ( Transaction tx = db.beginTx();){
				Result result = tx.execute(query);
				System.out.println(result.resultAsString());
				while(result.hasNext()){
						Map<String, Object> row = result.next();
						String name = (String) row.get("name");
						String position = (String) row.get("position");
						float score = (float) row.get("similarityScore");
						
						System.out.printf("Match: %s (Score: %.4f)%n", name, score);
        				System.out.println("Biography: " + position);
								
					}
				tx.commit();
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}

	}

	public static void main(String[] args) {
		final String neo4jFolder = args[0], database = args[1] ,fileName = args[2];

		loadPlayerData(neo4jFolder, database);
		embeddings(neo4jFolder, database);
		createVectorIndex(neo4jFolder,database);
		findSimilarPlayers(neo4jFolder, database);
		exportGraphML(neo4jFolder, database, fileName);
		
	}

	private static DatabaseManagementService getNeo4jConnection(String neo4jFolder, String database) {
		DatabaseManagementServiceBuilder builder = new DatabaseManagementServiceBuilder(Path.of(neo4jFolder, database))
				// This is necessary when dealing with large transactions... does it work?
				.setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
				.setConfig(GraphDatabaseSettings.preallocate_logical_logs, false)
				.setConfig(GraphDatabaseSettings.memory_transaction_database_max_size, 0l)
				// This cleans the transaction files every 5 secs.
				.setConfig(GraphDatabaseSettings.check_point_interval_time, Duration.ofSeconds(5l));

		DatabaseManagementService service = builder.build();

		registerShutdownHook(service);

		return service;
	}

	private static void registerShutdownHook(final DatabaseManagementService service) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				service.shutdown();
			}
		});
	}

}
