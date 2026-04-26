package dev.omatheusmesmo.ai;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTool {

    @Tool(description = "Adds two numbers and returns the result")
    public double add(
            @ToolParam(description = "First number") double a,
            @ToolParam(description = "Second number") double b) {
        return a + b;
    }

    @Tool(description = "Multiplies two numbers and returns the result")
    public double multiply(
            @ToolParam(description = "First number") double a,
            @ToolParam(description = "Second number") double b) {
        return a * b;
    }

    @Tool(description = "Returns the current date and time")
    public String currentDateTime() {
        return java.time.LocalDateTime.now().toString();
    }
}
