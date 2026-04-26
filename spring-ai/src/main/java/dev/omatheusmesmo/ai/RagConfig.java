package dev.omatheusmesmo.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RagConfig {

    private final EmbeddingModel embeddingModel;

    public RagConfig(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Bean
    public VectorStore vectorStore() throws IOException {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:rag-docs/*.txt");

        List<Document> allDocuments = new ArrayList<>();
        for (Resource resource : resources) {
            TextReader textReader = new TextReader(resource);
            List<Document> documents = textReader.get();
            allDocuments.addAll(documents);
        }

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(200)
                .withMinChunkSizeChars(50)
                .build();
        List<Document> splitDocuments = splitter.apply(allDocuments);

        vectorStore.add(splitDocuments);
        return vectorStore;
    }
}
