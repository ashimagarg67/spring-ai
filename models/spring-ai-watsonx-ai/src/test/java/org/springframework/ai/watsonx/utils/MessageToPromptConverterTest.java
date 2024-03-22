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
package org.springframework.ai.watsonx.utils;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.FunctionMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 */
public class MessageToPromptConverterTest {

	private MessageToPromptConverter converter;

	@Before
	public void setUp() {
		converter = MessageToPromptConverter.create().withHumanPrompt("").withAssistantPrompt("");
	}

	@Test
	public void testSingleUserMessage() {
		Message userMessage = new UserMessage("User message");
		String expected = "User message";
		Assert.assertEquals(expected, converter.messageToString(userMessage));
	}

	@Test
	public void testSingleAssistantMessage() {
		Message assistantMessage = new AssistantMessage("Id", 0, true, "Assistant message", Map.of());
		String expected = "Assistant message";
		Assert.assertEquals(expected, converter.messageToString(assistantMessage));
	}

	@Disabled
	public void testFunctionMessageType() {
		Message functionMessage = new FunctionMessage("testFunction", "Unsupported message", Map.of());
		Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
			converter.messageToString(functionMessage);
		});
	}

	@Test
	public void testSystemMessageType() {
		Message systemMessage = new SystemMessage("System message");
		String expected = "System message";
		Assert.assertEquals(expected, converter.messageToString(systemMessage));
	}

	@Test
	public void testCustomHumanPrompt() {
		converter.withHumanPrompt("Custom Human: ");
		Message userMessage = new UserMessage("User message");
		String expected = "Custom Human: User message";
		Assert.assertEquals(expected, converter.messageToString(userMessage));
	}

	@Test
	public void testCustomAssistantPrompt() {
		converter.withAssistantPrompt("Custom Assistant: ");
		Message assistantMessage = new AssistantMessage("id", 0, true, "Assistant message", Map.of());
		String expected = "Custom Assistant: Assistant message";
		Assert.assertEquals(expected, converter.messageToString(assistantMessage));
	}

	@Test
	public void testEmptyMessageList() {
		String expected = "";
		Assert.assertEquals(expected, converter.toPrompt(List.of()));
	}

	@Test
	public void testSystemMessageList() {
		String msg = "this is a LLM prompt";
		SystemMessage message = new SystemMessage(msg);
		Assert.assertEquals(msg, converter.toPrompt(List.of(message)));
	}

	@Test
	public void testUserMessageList() {
		List<Message> messages = List.of(new UserMessage("User message"));
		String expected = "User message";
		Assert.assertEquals(expected, converter.toPrompt(messages));
	}

	@Disabled
	public void testUnsupportedMessageType() {
		List<Message> messages = List.of(new FunctionMessage("testFunction", "Unsupported message", Map.of()));
		Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
			converter.toPrompt(messages);
		});
	}

}
