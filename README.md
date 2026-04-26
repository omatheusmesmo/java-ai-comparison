# Java AI Libraries Comparison

Side-by-side comparison of **LangChain4j**, **Spring AI**, and **Quarkus LangChain4j** for building AI-powered Java applications with Ollama.

## Features

All three projects implement the same capabilities:

- **Chat** — multi-user conversation with memory (`/ai/chat`)
- **RAG** — Retrieval-Augmented Generation with local documents (`/ai/rag`)
- **Tools** — Function calling (calculator: add, multiply, current date) (`/ai/tools`)

## Requirements

- **Java 25** (LTS)
- **Ollama** running locally with models:
  - `qwen3:1.7b` (chat)
  - `nomic-embed-text` (embeddings)

## Projects

| Project | Directory | Port | Framework |
|---|---|---|---|
| LangChain4j Pure | `langchain4j-pure/` | 8081 | Javalin 6.7 |
| Spring AI | `spring-ai/` | 9090 | Spring Boot 3.5 |
| Quarkus LangChain4j | `quarkus-langchain4j/` | 8087 | Quarkus 3.34 |

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
```

## Test

```bash
# Chat with memory
curl http://localhost:8081/ai/chat?userId=u1&message=Hello

# RAG query
curl http://localhost:8081/ai/rag?userId=u1&message=What+is+Quarkus?

# Tool calling
curl http://localhost:8081/ai/tools?userId=u1&message=What+is+5+times+7
```

## Article

This comparison accompanies the blog post: [Java AI Libraries Compared](https://blog.omatheusmesmo.dev/posts/java-ai-libraries-compared/)

## License

MIT
