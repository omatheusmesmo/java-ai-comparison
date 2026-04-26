# Java AI Libraries Comparison

Comparison of three approaches to building AI-powered Java applications using **Ollama** (local LLM) with **qwen3:1.7b** for chat and **nomic-embed-text** for embeddings.

## Projects

| | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| **Version** | 1.1.0 | 1.0.0 (GA) | 3.34.6 (Quarkus) |
| **Web Framework** | Javalin 6.4.0 | Spring Boot 3.5.0 | Quarkus (JAX-RS) |
| **Java** | 25 | 25 | 25 |
| **Maven Dependencies** | 8 | 4 | 9 (managed by BOM) |

## Code Size Comparison

| File | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| Main/Resource | `Application.java` (117) | `AiResource.java` (95) | `AiResource.java` (29) |
| AI Service | `Assistant.java` (11) | _(inline)_ | `Assistant.java` (18) |
| Tools | `CalculatorTool.java` (21) | `CalculatorTool.java` (28) | `CalculatorTool.java` (23) |
| RAG Config | _(inline in Application)_ | `RagConfig.java` (52) | _(zero — EasyRAG)_ |
| App Entry | _(same Application)_ | `Application.java` (11) | _(auto-generated)_ |
| **Total Java LOC** | **149** | **186** | **70** |
| Config | `logback.xml` (13) | `application.yml` (14) | `application.properties` (14) |
| **pom.xml** | 88 | 58 | 136 |

## Feature-by-Feature: Chat

### LangChain4j Pure — 117 lines just for wiring

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
    .contentRetriever(contentRetriever)
    .tools(calculatorTool)
    .build();
```

### Spring AI — ~20 lines with Advisor pattern

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(new InMemoryChatMemoryRepository())
    .maxMessages(20)
    .build();

MessageChatMemoryAdvisor memoryAdvisor =
    MessageChatMemoryAdvisor.builder(chatMemory).build();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(memoryAdvisor)
    .build();
```

### Quarkus LangChain4j — 0 lines of wiring

```java
@RegisterAiService(tools = CalculatorTool.class)
public interface Assistant {
    String chat(@MemoryId String userId, @UserMessage String message);
}
```

All wiring is done by CDI + configuration properties. No builder, no manual assembly.

## Feature-by-Feature: RAG

### LangChain4j Pure — ~30 lines manual pipeline

```java
EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

List<Document> documents = FileSystemDocumentLoader.loadDocuments(
    Path.of("src/main/resources/rag-docs"), new TextDocumentParser());

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
```

### Spring AI — 52 lines in RagConfig.java + manual augmentation in AiResource

```java
// RagConfig.java — ETL pipeline
@Bean
public VectorStore vectorStore() throws IOException {
    SimpleVectorStore vectorStore =
        SimpleVectorStore.builder(embeddingModel).build();

    PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();
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

// AiResource.java — manual RAG augmentation (QuestionAnswerAdvisor
// replaces the user prompt entirely, which breaks general chat)
private String augmentWithRag(String query) {
    List<Document> documents = vectorStore.similaritySearch(
        SearchRequest.builder().query(query).topK(3)
            .similarityThreshold(0.5).build());
    if (documents.isEmpty()) return query;
    String context = documents.stream()
        .map(Document::getText).collect(Collectors.joining("\n\n"));
    return """
        Context information is below.
        ---------------------
        %s
        ---------------------
        Answer the query. Use the context above if it is relevant,
        otherwise use your own knowledge.
        Query: %s""".formatted(context, query);
}
```

Note: Spring AI's `QuestionAnswerAdvisor` default prompt instructs the model
to **only** answer from context, rejecting general chat. We work around this
by manually augmenting the user message — matching LangChain4j's behavior.

### Quarkus LangChain4j — 3 properties, 0 Java code

```properties
quarkus.langchain4j.easy-rag.path=/path/to/rag-docs
quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
quarkus.langchain4j.easy-rag.max-segment-size=200
```

That's it. No Java code needed for RAG.

## Feature-by-Feature: Tools / Function Calling

### LangChain4j Pure

```java
@Tool("Adds two numbers")
public double add(@P("First") double a, @P("Second") double b) {
    return a + b;
}
```

Registered via `AiServices.builder(...).tools(calculatorTool).build()`

### Spring AI

```java
@Tool(description = "Adds two numbers")
public double add(
    @ToolParam(description = "First number") double a,
    @ToolParam(description = "Second number") double b) {
    return a + b;
}
```

Registered per-request: `.tools(calculatorTool)` on the prompt

### Quarkus LangChain4j

```java
@Tool("Adds two numbers")
public double add(@P("First") double a, @P("Second") double b) {
    return a + b;
}
```

Registered via `@RegisterAiService(tools = CalculatorTool.class)` or `@ToolBox(CalculatorTool.class)`

## Feature-by-Feature: Chat Memory

### LangChain4j Pure

```java
.chatMemoryProvider(memoryId ->
    MessageWindowChatMemory.withMaxMessages(20))
```

Must manually pass `memoryId` via `@MemoryId` on the interface.

### Spring AI

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(new InMemoryChatMemoryRepository())
    .maxMessages(20).build();

MessageChatMemoryAdvisor memoryAdvisor =
    MessageChatMemoryAdvisor.builder(chatMemory).build();

// Per-request:
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
```

Requires explicit advisor + per-request param passing.

### Quarkus LangChain4j

```properties
quarkus.langchain4j.chat-memory.type=message-window
quarkus.langchain4j.chat-memory.memory-window.max-messages=20
```

```java
String chat(@MemoryId String userId, @UserMessage String message);
```

Automatic via CDI — just annotate the parameter.

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

## Summary

| Metric | LangChain4j Pure | Spring AI | Quarkus LangChain4j |
|---|---|---|---|
| Java LOC (total) | 149 | 186 | **70** |
| RAG Java LOC | ~30 | 52 + 15 manual augmentation | **0** |
| Manual wiring | Extensive | Moderate | **None** |
| Dependencies to manage | 8 explicit | 4 (+ BOM) | **BOM-managed** |
| Learning curve | High | Medium | **Low** |

**Bottom line**: Quarkus LangChain4j achieves the same functionality with **53% less code** than LangChain4j Pure and **62% less** than Spring AI. RAG requires zero Java code. The combination of CDI, declarative annotations, and EasyRAG makes it the most productive choice for Java AI applications.

## Caveat: Spring AI QuestionAnswerAdvisor

Spring AI's `QuestionAnswerAdvisor` uses a default prompt that instructs the model to **only** answer from the provided context, rejecting questions outside the RAG scope (e.g., "Hello" → "I can't answer, it's not in the context"). This required a manual workaround — augmenting the user message directly with a custom prompt template and a `similarityThreshold(0.5)` to avoid injecting irrelevant context for general chat queries. By contrast, LangChain4j's `ContentRetriever` and Quarkus's `EasyRAG` inject context as supplementary information without restricting the model.
