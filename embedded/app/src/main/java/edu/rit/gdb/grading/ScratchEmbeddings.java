package edu.rit.gdb.grading;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.security.SecureRandom;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

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

import io.micrometer.observation.ObservationRegistry;

public class ScratchEmbeddings {


	public static void accessEmbeddings(String neo4jFolder,String database){

		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {
			SecureRandom secureRand = new SecureRandom();
			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
            String query = """
						MATCH (a:Player {name: "Alessandro Budel"})
						RETURN a
						""";
			try ( Transaction tx = db.beginTx();){
				try (Result result = tx.execute(query)){
					while(result.hasNext()){
						Map<String, Object> row = result.next();
						Node player = (Node) row.get("a");
						float[] embedding = (float[]) player.getProperty("embedding");
						float secureFloat = secureRand.nextFloat();
						embedding[0] = secureFloat;
						player.setProperty("embedding", embedding);
						tx.commit();
					}
				}catch (Exception oops) {

				}
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}
		
	}

		public static void getChangedEmbeddings(String neo4jFolder,String database){

		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {
			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
            String query = """
						MATCH (a:Player {name: "Alessandro Budel"})
						RETURN a
						""";
			try ( Transaction tx = db.beginTx();){
				try (Result result = tx.execute(query)){
					while(result.hasNext()){
						Map<String, Object> row = result.next();
						Node player = (Node) row.get("a");
						float[] embedding = (float[]) player.getProperty("embedding");
						for (float i: embedding){
							System.out.printf("(Embedding: %.4f)%n", i);
						}	
					}
				}catch (Exception oops) {

				}
				tx.commit();
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}
		
	}

	public static void embeddings(String neo4jFolder,String database){		
		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			// This is only for RETURN-type queries, that is, you want to retrieve something.
			String query = "MATCH (p:Player) RETURN  p LIMIT 10";
			
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
							"embedding", new float[]{ 0.23f, -0.89f, 0.41f, 0.12f}
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
							`vector.dimensions`: 4,
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


	public static void main(String[] args) {
		final String neo4jFolder = args[0], database = args[1];

		// loadPlayerData(neo4jFolder, database);
		// embeddings(neo4jFolder, database);
		// createVectorIndex(neo4jFolder,database);
		getChangedEmbeddings(neo4jFolder,database);
		accessEmbeddings(neo4jFolder, database);
		getChangedEmbeddings(neo4jFolder,database);
		// findSimilarPlayers(neo4jFolder, database);
		// showProcedures(neo4jFolder, database);
		
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
