package io.drive.api.dto;

import io.drive.api.model.FileNode;

import java.time.Instant;

public record FileNodeDto(
        String id,
        String name,
        String parentId,
        boolean folder,
        long size,
        String contentType,
        boolean trashed,
        Instant trashedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileNodeDto from(FileNode node) {
        return new FileNodeDto(
                node.getId(),
                node.getName(),
                node.getParentId(),
                node.isFolder(),
                node.getSize(),
                node.getContentType(),
                node.isTrashed(),
                node.getTrashedAt(),
                node.getCreatedAt(),
                node.getUpdatedAt());
    }
}
