package dev.omatheusmesmo.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiResource {

    private final ChatClient chatClient;
    private final ChatClient ragChatClient;
    private final ChatClient toolsChatClient;
    private final CalculatorTool calculatorTool;

    public AiResource(
            ChatModel chatModel,
            VectorStore vectorStore,
            ChatMemory chatMemory,
            CalculatorTool calculatorTool) {
        this.calculatorTool = calculatorTool;

        MessageChatMemoryAdvisor memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory).build();

        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.5)
                        .vectorStore(vectorStore)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();

        this.ragChatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor, ragAdvisor)
                .build();

        this.toolsChatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam("user") String userId,
            @RequestParam("q") String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .call()
                .content();
    }

    @GetMapping("/rag")
    public String rag(
            @RequestParam("user") String userId,
            @RequestParam("q") String question) {
        return ragChatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .call()
                .content();
    }

    @GetMapping("/tools")
    public String tools(
            @RequestParam("user") String userId,
            @RequestParam("q") String question) {
        return toolsChatClient.prompt()
                .user(question)
                .tools(calculatorTool)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .call()
                .content();
    }
}
