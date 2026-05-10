package com.javacli.tool;

import com.javacli.llm.LlmClient;

import java.util.List;

public record ToolOutput(String text, List<LlmClient.ContentPart> imageParts) {
    public ToolOutput {
        text = text == null ? "" : text;
        imageParts = imageParts == null ? List.of() : List.copyOf(imageParts);
    }

    public static ToolOutput text(String text) {
        return new ToolOutput(text, List.of());
    }

    public boolean hasImageParts() {
        return !imageParts.isEmpty();
    }
}
