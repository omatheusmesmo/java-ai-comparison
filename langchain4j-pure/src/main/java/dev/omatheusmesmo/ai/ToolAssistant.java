package dev.omatheusmesmo.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

interface ToolAssistant {
    String chat(@MemoryId String userId, @UserMessage String message);
}
