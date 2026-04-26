package dev.omatheusmesmo.ai;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/ai")
public class AiResource {

    @Inject
    Assistant assistant;

    @Inject
    RagAssistant ragAssistant;

    @GET
    @Path("/chat")
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("user") String userId, @QueryParam("q") String question) {
        return assistant.chat(userId, question);
    }

    @GET
    @Path("/tools")
    @Produces(MediaType.TEXT_PLAIN)
    public String tools(@QueryParam("user") String userId, @QueryParam("q") String question) {
        return assistant.chatWithTools(userId, question);
    }

    @GET
    @Path("/rag")
    @Produces(MediaType.TEXT_PLAIN)
    public String rag(@QueryParam("user") String userId, @QueryParam("q") String question) {
        return ragAssistant.chat(userId, question);
    }
}
