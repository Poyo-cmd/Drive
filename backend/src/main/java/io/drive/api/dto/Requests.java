package io.drive.api.dto;

import jakarta.validation.constraints.NotBlank;

public class Requests {

    public record CreateFolderRequest(
            @NotBlank String name,
            String parentId
    ) {
    }

    public record RenameRequest(
            @NotBlank String name
    ) {
    }
}
