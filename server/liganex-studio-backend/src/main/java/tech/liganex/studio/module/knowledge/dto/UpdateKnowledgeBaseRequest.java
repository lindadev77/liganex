package tech.liganex.studio.module.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateKnowledgeBaseRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1000) String description) {
}
