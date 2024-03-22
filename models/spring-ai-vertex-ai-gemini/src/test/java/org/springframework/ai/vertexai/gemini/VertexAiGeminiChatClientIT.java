/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.gemini;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.cloud.vertexai.VertexAI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions.TransportType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
class VertexAiGeminiChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(VertexAiGeminiChatClientIT.class);

	@Autowired
	private VertexAiGeminiChatClient client;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void multipleStreamRequests() {

		Flux<ChatResponse> joke1 = client.stream(new Prompt(new UserMessage("Tell me a joke?")));
		Flux<ChatResponse> joke2 = client.stream(new Prompt(new UserMessage("Tell me a toy joke?")));

		List<ChatResponse> joke1List = joke1.collectList().block();
		List<ChatResponse> joke2List = joke2.collectList().block();

		String id1 = joke1List.get(0).getId();
		joke1List.stream().forEach(response -> assertThat(response.getId()).isEqualTo(id1));
		joke1List.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getId)
			.forEach(id -> assertThat(id).isEqualTo(id1));

		String id2 = joke2List.get(0).getId();
		assertThat(id2).isNotEqualTo(id1);
		joke2List.stream().forEach(response -> assertThat(response.getId()).isEqualTo(id2));
		joke2List.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getId)
			.forEach(id -> assertThat(id).isEqualTo(id2));
	}

	@Test
	void roleTest() {
		String request = "Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.";
		String name = "Bob";
		String voice = "pirate";
		UserMessage userMessage = new UserMessage(request);
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = client.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");

		assertThat(response.getId()).isNotBlank();
		var generation = response.getResults().get(0);

		assertThat(generation.getIndex()).isEqualTo(0);
		assertThat(generation.isCompleted()).isTrue();

		AssistantMessage assistantMessage = generation.getOutput();
		assertThat(assistantMessage.getId()).isEqualTo(response.getId());
		assertThat(assistantMessage.getIndex()).isEqualTo(generation.getIndex());
		assertThat(assistantMessage.isCompleted()).isTrue();

		logger.info("Output Message properties: {}", generation.getOutput().getProperties().toString());
	}

	@Test
	void outputParser() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputParser outputParser = new ListOutputParser(conversionService);

		String format = outputParser.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors.", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.client.call(prompt).getResult();

		List<String> list = outputParser.parse(generation.getOutput().getContent());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputParser() {
		MapOutputParser outputParser = new MapOutputParser();

		String format = outputParser.getFormat();
		String template = """
				Provide me a List of {subject}
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = client.call(prompt).getResult();

		Map<String, Object> result = outputParser.parse(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = client.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputParser.parse(generation.getOutput().getContent());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void textStream() {

		String generationTextFromStream = client.stream(new Prompt("Explain Bulgaria? Answer in 10 paragraphs."))
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		// logger.info("" + actorsFilms);
		assertThat(generationTextFromStream).isNotEmpty();
	}

	@Test
	void beanStreamOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		Flux<ChatResponse> response = client.stream(prompt);

		List<ChatResponse> chatResponseList = response.collectList().block();

		String generationTextFromStream = chatResponseList.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputParser.parse(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);

		assertThat(chatResponseList).hasSizeGreaterThan(1);

		var firstResponse = chatResponseList.get(0);

		for (int i = 0; i < chatResponseList.size() - 1; i++) {
			var responseX = chatResponseList.get(i);
			assertThat(responseX.getId()).isEqualTo(firstResponse.getId());

			assertThat(responseX.getResults()).hasSize(1);
			var generation = responseX.getResults().get(0);

			assertThat(generation.getId()).isEqualTo(firstResponse.getId());
			assertThat(generation.getIndex()).isEqualTo(0);
			assertThat(generation.isCompleted()).isFalse();

			AssistantMessage assistantMessage = generation.getOutput();

			assertThat(assistantMessage.getId()).isEqualTo(firstResponse.getId());
			assertThat(assistantMessage.getIndex()).isEqualTo(0);
			assertThat(assistantMessage.isCompleted()).isFalse();

			logger.info("Output Message properties: {}", assistantMessage.getProperties().toString());
		}

		var lastResponse = chatResponseList.get(chatResponseList.size() - 1);
		assertThat(lastResponse.getId()).isEqualTo(firstResponse.getId());
		assertThat(lastResponse.getResults()).hasSize(1);
		var lastGeneration = lastResponse.getResults().get(0);

		assertThat(lastGeneration.getId()).isEqualTo(firstResponse.getId());
		assertThat(lastGeneration.getIndex()).isEqualTo(0);
		assertThat(lastGeneration.isCompleted()).isTrue();

		AssistantMessage lastAssistantMessage = lastGeneration.getOutput();

		assertThat(lastAssistantMessage.getId()).isEqualTo(firstResponse.getId());
		assertThat(lastAssistantMessage.getIndex()).isEqualTo(0);
		assertThat(lastAssistantMessage.isCompleted()).isTrue();

		logger.info("Output Message properties: {}", lastAssistantMessage.getProperties().toString());
	}

	@Test
	void multiModalityTest() throws IOException {

		byte[] data = new ClassPathResource("/vertex.test.png").getContentAsByteArray();

		var userMessage = new UserMessage("Explain what do you see o this picture?",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG, data)));

		ChatResponse response = client.call(new Prompt(List.of(userMessage)));

		// Response should contain something like:
		// I see a bunch of bananas in a golden basket. The bananas are ripe and yellow.
		// There are also some red apples in the basket. The basket is sitting on a table.
		// The background is a blurred light blue color.'
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple", "basket");

		// Error with image from URL:
		// com.google.api.gax.rpc.InvalidArgumentException:
		// io.grpc.StatusRuntimeException: INVALID_ARGUMENT: Only GCS URIs are supported
		// in file_uri and please make sure that the path is a valid GCS path.

		// String imageUrl =
		// "https://storage.googleapis.com/github-repo/img/gemini/multimodality_usecases_overview/banana-apple.jpg";

		// userMessage = new UserMessage("Explain what do you see o this picture?",
		// List.of(new Media(MimeTypeDetector.getMimeType(imageUrl), imageUrl)));
		// response = client.call(new Prompt(List.of(userMessage)));

		// assertThat(response.getResult().getOutput().getContent()).contains("bananas",
		// "apple", "basket");

		// https://github.com/GoogleCloudPlatform/generative-ai/blob/main/gemini/use-cases/intro_multimodal_use_cases.ipynb
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public VertexAI vertexAiApi() {
			String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
			String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
			return new VertexAI(projectId, location);
		}

		@Bean
		public VertexAiGeminiChatClient vertexAiEmbedding(VertexAI vertexAi) {
			return new VertexAiGeminiChatClient(vertexAi,
					VertexAiGeminiChatOptions.builder()
						.withModel(VertexAiGeminiChatClient.ChatModel.GEMINI_PRO_VISION.getValue())
						.withTransportType(TransportType.REST)
						.build());
		}

	}

}
