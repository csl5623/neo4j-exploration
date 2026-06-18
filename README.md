
## POC to Explore Vector Embeddings in Neo4j Java Embedded

### POC 1: explores creating vector embedddings using Ollama models and indexes using Neo4j embedded in Java

- Program :
    - loads data into Neo4j database
    -  creates embeddings with open source Ollama models
    - Computes embeddings similarities
    - Creates a vector index
    - Uses SEARCH cypher clause to find similar nodes based on embeddings 
    - Exports data into GraphML file

Resources:
- Embeded Neo4j in Java: https://neo4j.com/docs/java-reference/current/java-embedded/
- Embeddings and Vector Indexes tutorial:https://neo4j.com/docs/genai/tutorials/current/embeddings-vector-indexes/
- Spring AI framework to support AI models: https://docs.spring.io/spring-ai/reference/getting-started.html#dependency-management
    -Ollama: https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html 


Before running program:
- Dowload Ollama: https://docs.ollama.com/quickstart
    1. Pull embedding model
        1. https://ollama.com/library/nomic-embed-text
- In build.gradle uncomment:  mainClass = 'edu.rit.gdb.grading.EmbeddingsEmbedded'

To run program:

[Set $JAVA_HOME.]

Run:


./gradlew build

./gradlew installDist

./app/build/install/app/bin/app DB_FOLDER database GRAPHML_FILE 


Instructions:

- It receives three command-line parameters:

	1) The folder that stores the Neo4j database.

	2) The GraphML filename to export data to. File will be created in DB folder

- There must be no database running in the background.


## POC 2: Import Knowledge Graphs 
Before running program:

- In build.gradle uncomment:  mainClass = 'edu.rit.gdb.grading.ImportGraphML'
- Make sure : KGs_Export_Original directory is available in DB_FOLDER/database

To run program:

[Set $JAVA_HOME.]

Run:


./gradlew build

./gradlew installDist

./app/build/install/app/bin/app DB_FOLDER database GRAPHML_FILE 
