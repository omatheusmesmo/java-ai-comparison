# Java AI Libraries Comparison

Comparison of three approaches to building AI-powered Java applications using **Ollama** (local LLM) with **qwen3:1.7b** for chat and **nomic-embed-text** for embeddings.

All three projects implement the same 3-endpoint design:
- `/ai/chat` — chat with memory, no RAG
- `/ai/rag` — chat with RAG + memory
- `/ai/tools` — tool calling with memory, no RAG

## Projects

| | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| **Version** | 1.13.1 | 1.1.4 | 3.34.6 (Quarkus) |
| **Web Framework** | Javalin 6.7.0 | Spring Boot 3.5.0 | Quarkus (JAX-RS) |
| **Java** | 25 | 25 | 25 |
| **RAG Approach** | Manual ContentRetriever | RetrievalAugmentationAdvisor | EasyRAG (3 properties) |
| **Memory Approach** | chatMemoryProvider lambda | Auto-configured ChatMemory bean | application.properties + @ApplicationScoped |

## Code Size Comparison

| File | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| Main/Resource | `Application.java` (145) | `AiResource.java` (91) | `AiResource.java` (39) |
| AI Service(s) | `Assistant.java` (9), `RagAssistant.java` (8), `ToolAssistant.java` (8) | _(inline ChatClients)_ | `Assistant.java` (17), `RagAssistant.java` (12) |
| Tools | `CalculatorTool.java` (22) | `CalculatorTool.java` (28) | `CalculatorTool.java` (24) |
| RAG Config | _(inline in Application)_ | `RagConfig.java` (50) | _(zero — EasyRAG)_ |
| App Entry | _(same Application)_ | `Application.java` (11) | _(auto-generated)_ |
| **Total Java LOC** | **192** | **180** | **92** |
| Config | `logback.xml` (13) | `application.yml` (14) | `application.properties` (15) |

## Feature-by-Feature: Chat + Memory

### LangChain4j Pure — builder wiring

```java
ChatModel chatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("qwen3:1.7b")
    .timeout(Duration.ofMinutes(5))
    .build();

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .chatMemoryProvider(memoryId ->
        MessageWindowChatMemory.withMaxMessages(20))
    .build();
```

Plus a separate `Assistant.java` interface, Javalin server setup, and route handlers — 145 lines in `Application.java` alone.

### Spring AI — Advisor pattern with auto-configured memory

```java
public AiResource(ChatModel chatModel, VectorStore vectorStore,
                  ChatMemory chatMemory, CalculatorTool calculatorTool) {
    MessageChatMemoryAdvisor memoryAdvisor =
        MessageChatMemoryAdvisor.builder(chatMemory).build();

    this.chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(memoryAdvisor)
        .build();
}
```

Spring AI 1.1.4 auto-configures a `ChatMemory` bean (InMemoryChatMemoryRepository + MessageWindowChatMemory, 20 messages). You still build each `ChatClient` manually and pass the conversation ID per-request.

### Quarkus LangChain4j — an interface and properties

```java
@RegisterAiService(retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
@ApplicationScoped
public interface Assistant {
    String chat(@MemoryId String userId, @UserMessage String message);
    @ToolBox(CalculatorTool.class)
    String chatWithTools(@MemoryId String userId, @UserMessage String message);
}
```

```properties
quarkus.langchain4j.ollama.chat-model.model-id=qwen3:1.7b
quarkus.langchain4j.ollama.timeout=5m
quarkus.langchain4j.chat-memory.type=message-window
quarkus.langchain4j.chat-memory.memory-window.max-messages=20
```

CDI handles model, memory, and tools wiring. `@ApplicationScoped` keeps memory alive across requests for multi-user support.

## Feature-by-Feature: RAG

### LangChain4j Pure — ~30 lines manual pipeline

```java
EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
    .baseUrl(OLLAMA_BASE_URL)
    .modelName("nomic-embed-text")
    .timeout(Duration.ofMinutes(5))
    .build();

EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

List<Document> documents = FileSystemDocumentLoader.loadDocuments(
    Path.of("src/main/resources/rag-docs"), new TextDocumentParser());

EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
    .documentSplitter(DocumentSplitters.recursive(200, 30))
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
```

Requires a separate `RagAssistant` interface wired with `.contentRetriever()`.

### Spring AI — RetrievalAugmentationAdvisor (1.1.4)

```java
// RagConfig.java — ETL pipeline (50 lines)
@Bean
public VectorStore vectorStore() throws IOException {
    SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:rag-docs/*.txt");

    List<Document> allDocuments = new ArrayList<>();
    for (Resource resource : resources) {
        TextReader textReader = new TextReader(resource);
        allDocuments.addAll(textReader.get());
    }

    TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(200).withMinChunkSizeChars(50).build();
    vectorStore.add(splitter.apply(allDocuments));
    return vectorStore;
}

// AiResource.java — separate ChatClient with RAG advisor
RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .similarityThreshold(0.5)
        .vectorStore(vectorStore)
        .build())
    .queryAugmenter(ContextualQueryAugmenter.builder()
        .allowEmptyContext(true)
        .build())
    .build();

this.ragChatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(memoryAdvisor, ragAdvisor)
    .build();
```

Spring AI 1.1.4's `RetrievalAugmentationAdvisor` with `ContextualQueryAugmenter.allowEmptyContext(true)` gracefully handles non-RAG queries — a significant improvement over the older `QuestionAnswerAdvisor`. Still requires a 50-line `RagConfig.java` for the ETL pipeline.

### Quarkus LangChain4j — 3 properties, 0 Java code

```properties
quarkus.langchain4j.easy-rag.path=src/main/resources/rag-docs
quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
quarkus.langchain4j.easy-rag.max-segment-size=200
```

The `EasyRAG` extension handles loading, splitting, embedding, storing, and retrieving. It auto-creates a `RetrievalAugmentor` CDI bean that `@RegisterAiService` picks up. `NoRetrievalAugmentorSupplier` opts out on non-RAG services. Caches embeddings to disk so you don't re-process on every restart.

## Feature-by-Feature: Tools / Function Calling

The tool definitions are nearly identical across all three. The difference is registration.

### LangChain4j Pure

```java
@Tool("Adds two numbers and returns the result")
public double add(@P("First number") double a, @P("Second number") double b) {
    return a + b;
}
```

Requires a separate `ToolAssistant` interface + `AiServices.builder(...).tools(calculatorTool).build()`.

### Spring AI

```java
@Tool(description = "Adds two numbers and returns the result")
public double add(
    @ToolParam(description = "First number") double a,
    @ToolParam(description = "Second number") double b) {
    return a + b;
}
```

Per-request registration: `.tools(calculatorTool)` on each ChatClient prompt.

### Quarkus LangChain4j

```java
@Tool("Adds two numbers and returns the result")
double add(@P("First number") double a, @P("Second number") double b) {
    return a + b;
}
```

Declarative via `@ToolBox(CalculatorTool.class)` on specific interface methods. The tool is always available when needed.

## Feature-by-Feature: Chat Memory

### LangChain4j Pure

```java
.chatMemoryProvider(memoryId ->
    MessageWindowChatMemory.withMaxMessages(20))
```

Must manually pass `memoryId` via `@MemoryId` on each interface. Separate `chatMemoryProvider` per `AiServices.builder()` call.

### Spring AI

```java
// Auto-configured in 1.1.4 (InMemoryChatMemoryRepository + 20-message window)
MessageChatMemoryAdvisor memoryAdvisor =
    MessageChatMemoryAdvisor.builder(chatMemory).build();

// Per-request:
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
```

Requires explicit advisor wiring + per-request param passing. The `ChatMemory` bean is auto-configured, but the `MessageChatMemoryAdvisor` and conversation ID binding are manual.

### Quarkus LangChain4j

```properties
quarkus.langchain4j.chat-memory.type=message-window
quarkus.langchain4j.chat-memory.memory-window.max-messages=20
```

```java
String chat(@MemoryId String userId, @UserMessage String message);
```

Just annotate the parameter. CDI handles the rest. `@ApplicationScoped` ensures the service (and its memory) persists across requests.

## Unique Quarkus LangChain4j Advantages

| Feature | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| Dev Services (auto-start Ollama) | No | No | **Yes** |
| Native Image (GraalVM) | Manual setup | Community support | **Out of the box** |
| Live Reload | No | DevTools (classpath) | **Dev Mode (instant)** |
| Config-driven RAG | No | No | **EasyRAG** |
| Zero-config AI Service | No | No | **@RegisterAiService** |
| Reactive Streaming | Manual | Flux return | **Multi (Mutiny)** |
| Timeout Configuration | Java code | YAML | **application.properties** |

## Runtime Metrics

Measured on Java 25, Linux, with Ollama running locally (qwen3:1.7b chat, nomic-embed-text embeddings). RSS measured after 60s warmup (JVM stabilized). See `scripts/measure.sh` for methodology.

### Cold Start (first startup, all re-embed documents)

| Metric | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| **Startup (self-reported)** | 125ms (Javalin) | ~3.0s (Spring Boot) | ~6.0s (Quarkus) |
| **Startup (wall-clock)** | ~1.1s | ~4.3s | ~6.4s |
| **RSS Memory** | ~114MB | ~331MB | ~247MB |

### Warm Start (Quarkus with cached embeddings)

| Metric | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| **Startup (self-reported)** | ~125ms (Javalin) | ~3.0s (Spring Boot) | ~0.9s (Quarkus) |
| **Startup (wall-clock)** | ~1.1s | ~4.3s | ~1.1s |
| **RSS Memory** | ~114MB | ~331MB | ~119MB |

Quarkus's Easy RAG `reuse-embeddings` caches computed embeddings to `easy-rag-embeddings.json`, avoiding the embedding API call on restart. This is a dev-mode convenience: in production with a persistent embedding store (PgVector, Redis, etc.), all three would skip re-embedding on startup.

### Document Parsing

| | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| **Parser used** | `TextDocumentParser` | `TextReader` | Apache Tika (via EasyRAG) |
| **Supported formats** | Plain text only | Plain text only | Text, PDF, DOCX, HTML, images (OCR) |

LangChain4j and Spring AI both support richer parsers as optional dependencies (`ApacheTikaDocumentParser`, `spring-ai-tika-document-reader`), but our demo projects use plain text parsers only. Quarkus EasyRAG uses Apache Tika by default, which adds to its cold-start overhead but provides multi-format support out of the box.

### Why LangChain4j Pure appears lighter

LangChain4j Pure shows lower RSS and faster startup not because it is better optimized, but because it does less: no DI container, no annotation processing, no auto-configuration, minimal web server (Javalin with embedded Jetty). Quarkus cold start pays the cost of initializing CDI, RESTEasy, Vert.x, Apache Tika, and the EasyRAG pipeline. In production, Quarkus can compile to a GraalVM native image, eliminating most of this overhead entirely (sub-second startup, ~30-50MB RSS), which neither LangChain4j Pure nor Spring AI can match without significant manual effort.

## Summary

| Metric | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| Java LOC (total) | 192 | 180 | **92** |
| RAG Java LOC | ~30 | 50 (RagConfig) | **0** |
| Separate AI Services | 3 interfaces + 3 builders | 3 ChatClients | 2 interfaces (declarative) |
| Manual wiring | Extensive | Moderate | **None** |
| Dependencies to manage | 8 explicit | 4 (+ BOM) | **BOM-managed** |
| Learning curve | High | Medium | **Low** |

**Bottom line**: Quarkus LangChain4j achieves the same functionality with **52% less code** than LangChain4j Pure and **49% less** than Spring AI. RAG requires zero Java code. The combination of CDI, declarative annotations, and EasyRAG makes it the most productive choice for Java AI applications.
