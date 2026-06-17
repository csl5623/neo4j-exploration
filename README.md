
## POC to Explore Vector Embeddings in Neo4j Java Embedded

Resources:
- Embeded Neo4j in Java: https://neo4j.com/docs/java-reference/current/java-embedded/
- Embeddings and Vector Indexes tutorial:https://neo4j.com/docs/genai/tutorials/current/embeddings-vector-indexes/
- Spring AI framework to support AI models: https://docs.spring.io/spring-ai/reference/getting-started.html#dependency-management
    -Ollama: https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html 

To install:

[Set $JAVA_HOME.]

Run:
./gradlew build

./gradlew installDist


To run program:

./app/build/install/app/bin/app DB_FOLDER database GRAPHML_FILE 


Instructions:

- It receives three command-line parameters:

	1) The folder that stores the Neo4j database.

	2) The GraphML filename to export data to. File will be created in DB folder

- There must be no database running in the background.


