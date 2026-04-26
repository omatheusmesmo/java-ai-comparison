package dev.omatheusmesmo.ai;

import java.time.Duration;
import java.util.List;
import java.nio.file.Path;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import io.javalin.Javalin;

public class Application {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String CHAT_MODEL_NAME = "qwen3:1.7b";
    private static final String EMBEDDING_MODEL_NAME = "nomic-embed-text";
    private static final String RAG_DOCS_PATH = "src/main/resources/rag-docs";

    public static void main(String[] args) {
        ChatModel chatModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(CHAT_MODEL_NAME)
                .timeout(Duration.ofMinutes(5))
                .logRequests(true)
                .logResponses(true)
                .build();

        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(EMBEDDING_MODEL_NAME)
                .timeout(Duration.ofMinutes(5))
                .build();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                Path.of(RAG_DOCS_PATH), new TextDocumentParser());

        DocumentSplitter splitter = DocumentSplitters.recursive(200, 30);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(documents);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        CalculatorTool calculatorTool = new CalculatorTool();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(20))
                .build();

        RagAssistant ragAssistant = AiServices.builder(RagAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(20))
                .contentRetriever(contentRetriever)
                .build();

        ToolAssistant toolAssistant = AiServices.builder(ToolAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(20))
                .tools(calculatorTool)
                .build();

        Javalin app = Javalin.create()
                .start(8081);

        app.get("/ai/chat", ctx -> {
            try {
                String userId = ctx.queryParam("user");
                String question = ctx.queryParam("q");
                if (userId == null || question == null) {
                    ctx.status(400).result("Missing query params: user, q");
                    return;
                }
                String response = assistant.chat(userId, question);
                ctx.contentType("text/plain").result(response);
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.get("/ai/rag", ctx -> {
            try {
                String userId = ctx.queryParam("user");
                String question = ctx.queryParam("q");
                if (userId == null || question == null) {
                    ctx.status(400).result("Missing query params: user, q");
                    return;
                }
                String response = ragAssistant.chat(userId, question);
                ctx.contentType("text/plain").result(response);
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        app.get("/ai/tools", ctx -> {
            try {
                String userId = ctx.queryParam("user");
                String question = ctx.queryParam("q");
                if (userId == null || question == null) {
                    ctx.status(400).result("Missing query params: user, q");
                    return;
                }
                String response = toolAssistant.chat(userId, question);
                ctx.contentType("text/plain").result(response);
            } catch (Exception e) {
                ctx.status(500).result("Error: " + e.getMessage());
            }
        });

        System.out.println("LangChain4j Pure server started on http://localhost:8081");
        System.out.println("Endpoints:");
        System.out.println("  GET /ai/chat?user=1&q=Hello");
        System.out.println("  GET /ai/rag?user=1&q=What is EasyRAG?");
        System.out.println("  GET /ai/tools?user=1&q=What is 5 times 7?");
    }
}
