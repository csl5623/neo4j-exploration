package edu.rit.gdb.grading;

import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.graphdb.Result;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Stream;

import org.neo4j.graphdb.Transaction;

import apoc.ApocConfig;
import apoc.export.graphml.ExportGraphML;

public class ImportGraphML {
    

    public static void importGraphML(String neo4jFolder,String database,String fileName){

		try (DatabaseManagementService service = getNeo4jConnection(neo4jFolder, database);) {

			GraphDatabaseService db = service.database(GraphDatabaseSettings.initial_default_database.defaultValue());
			
			DependencyResolver resolver = ((GraphDatabaseAPI) db).getDependencyResolver();
			GlobalProcedures procedures = resolver.resolveDependency(
					GlobalProcedures.class, DependencyResolver.SelectionStrategy.SINGLE);
			
			procedures.registerProcedure(ExportGraphML.class);
			
			ApocConfig.apocConfig().setProperty(ApocConfig.APOC_IMPORT_FILE_ENABLED, true);
			ApocConfig.apocConfig().setProperty(ApocConfig.APOC_EXPORT_FILE_ENABLED, true);

			Result res = null;
			try ( Transaction tx = db.beginTx();){
				res = tx.execute("CALL apoc.import.graphml(\""+fileName+"\", {readLabels: true, storeNodeIds: true})");
				System.out.println(res.resultAsString());
				tx.commit();
			}
			
			service.shutdown();
			
		} catch (Exception oops) {
			System.out.print("Dang it!");
			oops.printStackTrace();
		}
	}

    private static void openFiles(String neo4jFolder, String database,String dir){
        Path basePath = Path.of(neo4jFolder, database, dir);
        try (Stream<Path> stream = Files.walk(Path.of(neo4jFolder,database,dir))) {
            stream.filter(p-> (!p.getFileName().startsWith(".DS_Store")))
            .forEach(path -> {
                Path name = path.getFileName(); 
                if (Files.isDirectory(path)) {
                    System.out.println("Directory name: " + name);
                    
                } else {

                    String relativePathStr = basePath.relativize(path).toString();
                    String p = dir + "/" + relativePathStr;
                    System.out.println(" Path: " + p);
                    System.out.println("Relative Path: " + relativePathStr);
                    importGraphML(neo4jFolder,database,p);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
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

	public static void main(String[] args) {
		final String neo4jFolder = args[0], database = args[1]; 
        String KGs_dir = "KGs_Export_Original";
        openFiles(neo4jFolder, database, KGs_dir);

		
		
	}
}

