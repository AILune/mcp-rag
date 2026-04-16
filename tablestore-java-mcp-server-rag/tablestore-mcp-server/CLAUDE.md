# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and run

- Requires JDK 17+ and uses the Maven Wrapper (`mvnw`; use `mvnw.cmd` from Windows shells).
- Package without tests: `./mvnw package -DskipTests`
- Run tests: `./mvnw test`
- Run a single test class: `./mvnw -Dtest=ClassName test`
- Run a single test method: `./mvnw -Dtest=ClassName#methodName test`
- Start locally with Spring Boot: `./mvnw spring-boot:run`
- Run the packaged jar: `java -jar target/tablestore-java-mcp-server-1.0-SNAPSHOT.jar`

There is no dedicated lint/format target configured in `pom.xml`; Maven build/test is the main validation path. The README mentions `settings.xml`, but no such file is checked into this module.

## Runtime configuration

Configuration is driven entirely by environment variables in `src/main/java/com/alicloud/openservices/tablestore/sample/config/EnvironmentSettings.java`.

Required variables:
- `TABLESTORE_INSTANCE_NAME`
- `TABLESTORE_ENDPOINT`
- `TABLESTORE_ACCESS_KEY_ID`
- `TABLESTORE_ACCESS_KEY_SECRET`

Common optional overrides:
- `TABLESTORE_VECTOR_DIMENSION` (default `768`)
- `EMBEDDING_MODEL_NAME` (default `ai.djl.huggingface.rust/BAAI/bge-base-en-v1.5/0.0.1/bge-base-en-v1.5`)
- knowledge/FAQ table and index names
- primary-key, text, vector, and FAQ answer field names

`src/main/resources/application.properties` only sets the MCP server name and enables `ai.djl` DEBUG logging.

## Architecture overview

- `src/main/java/com/alicloud/openservices/tablestore/sample/App.java` is the Spring Boot entrypoint.
- `src/main/java/com/alicloud/openservices/tablestore/sample/mcp/TablestoreMcp.java` registers four MCP tools through Spring AI `FunctionToolCallback`s: `storeKnowledge`, `searchKnowledge`, `storeFAQ`, and `searchFAQ`.
- The classes under `src/main/java/com/alicloud/openservices/tablestore/sample/model/` define the tool payloads that become the MCP-facing schemas.

### Core flow

`src/main/java/com/alicloud/openservices/tablestore/sample/service/TablestoreService.java` is the center of the application. On construction it:
1. reads environment-driven settings,
2. creates the Tablestore `SyncClient`,
3. creates the knowledge and FAQ tables if they do not exist,
4. creates the corresponding search indexes if they do not exist,
5. validates that the embedding model dimension matches both the configured dimension and the remote index schema.

Startup therefore has remote side effects against Tablestore; it is not just local wiring.

`src/main/java/com/alicloud/openservices/tablestore/sample/service/EmbeddingService.java` loads a local DJL text-embedding model at startup and generates vectors on demand. If `EMBEDDING_MODEL_NAME` changes, `TABLESTORE_VECTOR_DIMENSION` and the existing Tablestore vector field schema must stay aligned or startup will fail.

### Storage model

The service maintains two logical stores:
- Knowledge store: document `content` plus optional `metaData`
- FAQ store: `question` plus `answer`

Both stores persist raw row data in Tablestore and rely on a search index containing:
- one text field for BM25/match queries
- one vector field for KNN retrieval

Important nuance: the indexed schema is minimal. Metadata columns and the FAQ answer column are stored in rows and returned because searches request all columns, but they are not added to the search index schema and are therefore not queryable unless the code changes.

### Retrieval behavior

Search behavior is intentionally different by store:
- Knowledge search is currently vector KNN only; the text-match branch is present but commented out.
- FAQ search is hybrid retrieval: vector KNN plus text `match` on the question field.

## Testing status

There are currently no test sources under `src/test/java`, so validation is mostly through `./mvnw test`, `./mvnw package`, and manual runtime checks against a real Tablestore instance.
