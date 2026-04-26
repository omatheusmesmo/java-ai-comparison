package dev.omatheusmesmo.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
@ApplicationScoped
public interface Assistant {

    String chat(@MemoryId String userId, @UserMessage String message);

    @ToolBox(CalculatorTool.class)
    String chatWithTools(@MemoryId String userId, @UserMessage String message);
}
