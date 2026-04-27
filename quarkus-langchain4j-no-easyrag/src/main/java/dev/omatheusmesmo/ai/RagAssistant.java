package dev.omatheusmesmo.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface RagAssistant {
    String chat(@MemoryId String userId, @UserMessage String message);
}
