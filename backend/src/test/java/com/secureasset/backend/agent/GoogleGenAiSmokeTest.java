package com.secureasset.backend.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class GoogleGenAiSmokeTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    void testGoogleGenAiConnection() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        String response = chatClient.prompt()
                .user("Return exactly: SECUREASSET_AI_OK")
                .call()
                .content();

        assertThat(response).contains("SECUREASSET_AI_OK");
    }
}
