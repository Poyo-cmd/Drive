package io.drive.api.controller;

import io.drive.api.dto.FileNodeDto;
import io.drive.api.dto.Requests.CreateFolderRequest;
import io.drive.api.dto.Requests.RenameRequest;
import io.drive.api.model.FileNode;
import io.drive.api.service.FileService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    /** El id del usuario autenticado viene del subject del JWT (lo pone JwtAuthFilter). */
    private String userId(Authentication auth) {
        return auth.getName();
    }

    @GetMapping
    public List<FileNodeDto> list(Authentication auth, @RequestParam(required = false) String parentId) {
        return files.list(userId(auth), parentId).stream().map(FileNodeDto::from).toList();
    }

    @GetMapping("/trash")
    public List<FileNodeDto> trash(Authentication auth) {
        return files.listTrash(userId(auth)).stream().map(FileNodeDto::from).toList();
    }

    @GetMapping("/{id}")
    public FileNodeDto get(Authentication auth, @PathVariable String id) {
        return FileNodeDto.from(files.get(userId(auth), id));
    }

    @PostMapping("/folders")
    public ResponseEntity<FileNodeDto> createFolder(Authentication auth,
                                                    @Valid @RequestBody CreateFolderRequest req) {
        FileNode created = files.createFolder(userId(auth), req.name(), req.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(FileNodeDto.from(created));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileNodeDto> upload(Authentication auth,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String parentId) throws IOException {
        FileNode created = files.upload(userId(auth), parentId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(FileNodeDto.from(created));
    }

    @PatchMapping("/{id}")
    public FileNodeDto rename(Authentication auth, @PathVariable String id,
                             @Valid @RequestBody RenameRequest req) {
        return FileNodeDto.from(files.rename(userId(auth), id, req.name()));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(Authentication auth, @PathVariable String id) {
        files.restore(userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    /** Por defecto manda a la papelera; con ?permanent=true borra definitivamente. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable String id,
                                       @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            files.deleteForever(userId(auth), id);
        } else {
            files.moveToTrash(userId(auth), id);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash")
    public ResponseEntity<Void> emptyTrash(Authentication auth) {
        files.emptyTrash(userId(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(Authentication auth, @PathVariable String id) {
        FileNode node = files.get(userId(auth), id);
        ResponseInputStream<GetObjectResponse> stream = files.download(node);
        String filename = URLEncoder.encode(node.getName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        node.getContentType() != null ? node.getContentType() : "application/octet-stream"))
                .contentLength(node.getSize())
                .body(new InputStreamResource(stream));
    }
}
