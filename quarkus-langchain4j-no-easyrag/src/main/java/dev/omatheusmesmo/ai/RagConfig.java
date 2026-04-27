package dev.omatheusmesmo.ai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;
import java.util.List;

public class RagConfig {

    private volatile RetrievalAugmentor augmentor;

    void onStart(@Observes StartupEvent ev, EmbeddingModel embeddingModel) {
        augmentor = buildRetrievalAugmentor(embeddingModel);
    }

    @Produces
    @ApplicationScoped
    public RetrievalAugmentor retrievalAugmentor() {
        return augmentor;
    }

    private RetrievalAugmentor buildRetrievalAugmentor(EmbeddingModel embeddingModel) {
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        DocumentParser parser = new TextDocumentParser();
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                Path.of("src/main/resources/rag-docs"), parser);

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

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }
}

