# Java AI Libraries Comparison

Side-by-side comparison of **LangChain4j**, **Spring AI**, and **Quarkus LangChain4j** (with and without EasyRAG) for building AI-powered Java applications with Ollama.

## Features

All four projects implement the same capabilities:

- **Chat** — multi-user conversation with memory (`/ai/chat`)
- **RAG** — Retrieval-Augmented Generation with local documents (`/ai/rag`)
- **Tools** — Function calling (calculator: add, multiply, current date) (`/ai/tools`)

## Requirements

- **Java 25** (LTS)
- **Ollama** running locally with models:
  - `qwen3:1.7b` (chat)
  - `nomic-embed-text` (embeddings)

## Projects

| Project | Directory | Port | Framework | RAG Approach |
|---|---|---|---|---|
| LangChain4j Pure | `langchain4j-pure/` | 8081 | Javalin 6.7 | Manual ContentRetriever (~30 LOC) |
| Spring AI | `spring-ai/` | 9090 | Spring Boot 3.5 | RetrievalAugmentationAdvisor (50 LOC) |
| Quarkus LangChain4j (EasyRAG) | `quarkus-langchain4j/` | 8087 | Quarkus 3.34 | EasyRAG (0 LOC, 3 properties) |
| Quarkus LangChain4j (manual RAG) | `quarkus-langchain4j-no-easyrag/` | 8088 | Quarkus 3.34 | Manual RetrievalAugmentor (~25 LOC) |

## Quick Start

```bash
# Start Ollama (if not running)
ollama serve

# Pull required models
ollama pull qwen3:1.7b
ollama pull nomic-embed-text

# Run any project
cd langchain4j-pure && mvn compile exec:java
cd spring-ai && mvn spring-boot:run
cd quarkus-langchain4j && ./mvnw quarkus:dev
cd quarkus-langchain4j-no-easyrag && ./mvnw quarkus:dev
```

## Test

```bash
# Chat with memory
curl http://localhost:8081/ai/chat?userId=u1&message=Hello

# RAG query
curl http://localhost:8081/ai/rag?userId=u1&message=What+is+LangChain4j?

# Tool calling
curl http://localhost:8081/ai/tools?userId=u1&message=What+is+5+times+7
```

## Runtime Metrics

Measured on Java 25, Linux, with Ollama running locally. RSS measured after 60s warmup. See `scripts/Measure.java` for the measurement script (JBang).

### Cold Start (first startup, all re-embed documents)

| Metric | LC4j Pure | Spring AI | Quarkus LC4j + EasyRAG | Quarkus LC4j (manual RAG) |
|---|---|---|---|---|
| **Startup (wall-clock)** | ~2.0s | ~5.6s | ~7.0s | **~2.1s** |
| **Startup (self-reported)** | 181ms (Javalin only) | 4.9s | 6.8s | **2.0s** |
| **RSS Memory** | ~116MB | ~329MB | ~237MB | **~155MB** |

### Warm Start (Quarkus LangChain4j with EasyRAG, cached embeddings)

| Metric | LC4j Pure | Spring AI | Quarkus LC4j + EasyRAG (warm) | Quarkus LC4j (manual RAG) |
|---|---|---|---|---|
| **Startup (wall-clock)** | ~2.0s | ~5.6s | **~1.7s** | ~2.1s |
| **Startup (self-reported)** | 181ms (Javalin only) | 4.9s | **1.3s** | 2.0s |
| **RSS Memory** | ~116MB | ~329MB | **~123MB** | ~155MB |

### Document Parsing

| | LC4j Pure | Spring AI | Quarkus LC4j + EasyRAG | Quarkus LC4j (manual RAG) |
|---|---|---|---|---|
| **Parser** | `TextDocumentParser` | `TextReader` | Apache Tika | `TextDocumentParser` |
| **Formats** | Plain text | Plain text | Text, PDF, DOCX, HTML, OCR | Plain text |

The Quarkus LangChain4j manual RAG variant proves that the slower cold start with EasyRAG is caused by Apache Tika's classpath scanning, not by Quarkus itself. Adding Tika to LC4j Pure or Spring AI would produce similar startup overhead (~1 LOC change, same Tika cost).

## Article

This comparison accompanies the blog post:

- 🇧🇷 [Bibliotecas Java para IA Comparadas](https://blog.omatheusmesmo.dev/posts/bibliotecas-java-para-ia-comparadas/)
- 🇺🇸 [Java AI Libraries Compared](https://blog.omatheusmesmo.dev/en/posts/java-ai-libraries-compared/)

## License

MIT
