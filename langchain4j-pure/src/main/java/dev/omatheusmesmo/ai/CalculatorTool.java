package dev.omatheusmesmo.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CalculatorTool {

    @Tool("Adds two numbers and returns the result")
    public double add(@P("First number") double a, @P("Second number") double b) {
        return a + b;
    }

    @Tool("Multiplies two numbers and returns the result")
    public double multiply(@P("First number") double a, @P("Second number") double b) {
        return a * b;
    }

    @Tool("Returns the current date and time")
    public String currentDateTime() {
        return java.time.LocalDateTime.now().toString();
    }
}
