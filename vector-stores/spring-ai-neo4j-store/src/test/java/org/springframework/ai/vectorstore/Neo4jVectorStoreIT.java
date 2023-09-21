package org.springframework.ai.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springramework.ai.vectorstore.Neo4jVectorStore;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Gerrit Meier
 * @author Michael Simons
 */
@Testcontainers
public class Neo4jVectorStoreIT {

	// Neo4j 5.12 has a bug wrt checking limits, so either 5.11 or anything higher than
	// 5.12 works
	@Container
	static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.11"))
		.withRandomPassword();

	List<Document> documents = List.of(
			new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1")),
			new Document("Hello World Hello World Hello World Hello World Hello World Hello World Hello World"),
			new Document(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression",
					Collections.singletonMap("meta2", "meta2")));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("SPRING_AI_OPENAI_API_KEY"));

	@BeforeEach
	void cleanDatabase() {
		contextRunner
			.run(context -> context.getBean(Driver.class).executableQuery("MATCH (n) DETACH DELETE n").execute());
	}

	@Test
	void addAndSearchTest() {
		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			List<Document> results = vectorStore.similaritySearch("Great", 1);

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).isEqualTo(Collections.singletonMap("meta2", "meta2"));

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));

			List<Document> results2 = vectorStore.similaritySearch("Great", 1);
			assertThat(results2).hasSize(0);
		});
	}

	@Test
	void documentUpdateTest() {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore.similaritySearch("Spring", 5);

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).isEqualTo(Collections.singletonMap("meta1", "meta1"));

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch("FooBar", 5);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).isEqualTo(Collections.singletonMap("meta2", "meta2"));

		});
	}

	@Test
	void searchThresholdTest() {

		contextRunner.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class)).run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			assertThat(vectorStore.similaritySearch("Great", 5, 0)).hasSize(3);

			List<Document> results = vectorStore.similaritySearch("Great", 5, 0.89);

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getText()).isEqualTo(
					"Great Depression Great Depression Great Depression Great Depression Great Depression Great Depression");
			assertThat(resultDoc.getMetadata()).isEqualTo(Collections.singletonMap("meta2", "meta2"));

		});
	}

	@Test
	void ensureVectorIndexGetsCreated() {
		contextRunner.run(context -> {
			assertThat(context.getBean(Driver.class)
				.executableQuery(
						"SHOW indexes yield name, type WHERE name = 'spring-ai-document-index' AND type = 'VECTOR' return count(*) > 0")
				.execute()
				.records()
				.get(0) // get first record
				.get(0)
				.asBoolean()) // get returned result
				.isTrue();
		});
	}

	@Test
	void ensureIdIndexGetsCreated() {
		contextRunner.run(context -> {
			assertThat(context.getBean(Driver.class)
				.executableQuery(
						"SHOW indexes yield labelsOrTypes, properties, type WHERE any(x in labelsOrTypes where x = 'Document')  AND any(x in properties where x = 'id') AND type = 'RANGE' return count(*) > 0")
				.execute()
				.records()
				.get(0) // get first record
				.get(0)
				.asBoolean()) // get returned result
				.isTrue();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public VectorStore vectorStore(Driver driver, EmbeddingClient embeddingClient) {

			return new Neo4jVectorStore(driver, embeddingClient,
					Neo4jVectorStore.Neo4jVectorStoreConfig.defaultConfig());
		}

		@Bean
		public Driver driver() {
			return GraphDatabase.driver(neo4jContainer.getBoltUrl(),
					AuthTokens.basic("neo4j", neo4jContainer.getAdminPassword()));
		}

	}

}
