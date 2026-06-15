package io.drive.api.service;

import io.drive.api.config.AwsProperties;
import io.drive.api.exception.NotFoundException;
import io.drive.api.model.FileNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Logica de archivos y carpetas. Todo esta acotado por usuario (ownerId):
 * cada operacion verifica que el nodo pertenezca a quien hace la peticion,
 * evitando que un usuario acceda a archivos de otro (IDOR).
 */
@Service
public class FileService {

    private final S3Client s3;
    private final AwsProperties props;
    private final DynamoDbTable<FileNode> table;
    private final DynamoDbIndex<FileNode> parentIndex;

    public FileService(S3Client s3, DynamoDbEnhancedClient enhanced, AwsProperties props) {
        this.s3 = s3;
        this.props = props;
        this.table = enhanced.table(props.metadataTable(), TableSchema.fromBean(FileNode.class));
        this.parentIndex = table.index(FileNode.PARENT_INDEX);
    }

    /** Lista carpetas primero y luego archivos (ambos por nombre), excluyendo la papelera. */
    public List<FileNode> list(String userId, String parentId) {
        String parent = resolveParent(userId, parentId);
        if (!parent.equals(FileNode.rootOf(userId))) {
            requireFolder(userId, parent); // valida pertenencia de la carpeta
        }
        return childrenOf(parent).stream()
                .filter(n -> !n.isTrashed() && userId.equals(n.getOwnerId()))
                .sorted(Comparator.comparing(FileNode::isFolder).reversed()
                        .thenComparing(n -> n.getName().toLowerCase()))
                .toList();
    }

    public FileNode get(String userId, String id) {
        FileNode node = table.getItem(Key.builder().partitionValue(id).build());
        if (node == null || !userId.equals(node.getOwnerId())) {
            throw new NotFoundException("No existe el recurso " + id);
        }
        return node;
    }

    public FileNode createFolder(String userId, String name, String parentId) {
        FileNode node = newNode(userId, name, resolveParent(userId, parentId));
        node.setFolder(true);
        node.setSize(0);
        table.putItem(node);
        return node;
    }

    public FileNode upload(String userId, String parentId, MultipartFile file) throws IOException {
        FileNode node = newNode(userId, originalName(file), resolveParent(userId, parentId));
        String s3Key = "blobs/" + node.getId();
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";

        s3.putObject(builder -> builder
                        .bucket(props.bucket())
                        .key(s3Key)
                        .contentType(contentType),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        node.setFolder(false);
        node.setSize(file.getSize());
        node.setContentType(contentType);
        node.setS3Key(s3Key);
        table.putItem(node);
        return node;
    }

    public ResponseInputStream<GetObjectResponse> download(FileNode node) {
        if (node.isFolder() || node.getS3Key() == null) {
            throw new NotFoundException("El recurso no es un archivo descargable");
        }
        return s3.getObject(GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(node.getS3Key())
                .build());
    }

    public FileNode rename(String userId, String id, String name) {
        FileNode node = get(userId, id);
        node.setName(name);
        node.setUpdatedAt(Instant.now());
        table.putItem(node);
        return node;
    }

    // ---------- Papelera ----------

    /** Mueve a la papelera (soft-delete). En carpetas, arrastra todo su contenido. */
    public void moveToTrash(String userId, String id) {
        FileNode node = get(userId, id);
        markTrashed(node, true);
    }

    /** Restaura desde la papelera. En carpetas, restaura todo su contenido. */
    public void restore(String userId, String id) {
        FileNode node = get(userId, id);
        markTrashed(node, false);
    }

    /** Lista los elementos en la papelera del usuario, mas recientes primero. */
    public List<FileNode> listTrash(String userId) {
        return table.scan().items().stream()
                .filter(n -> n.isTrashed() && userId.equals(n.getOwnerId()))
                .sorted(Comparator.comparing(FileNode::getTrashedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Borrado definitivo de un nodo (y su contenido en S3 si aplica). */
    public void deleteForever(String userId, String id) {
        FileNode node = get(userId, id);
        purge(node);
    }

    /** Vacia la papelera del usuario por completo. */
    public void emptyTrash(String userId) {
        for (FileNode node : listTrash(userId)) {
            if (!node.isFolder() && node.getS3Key() != null) {
                s3.deleteObject(b -> b.bucket(props.bucket()).key(node.getS3Key()));
            }
            table.deleteItem(Key.builder().partitionValue(node.getId()).build());
        }
    }

    // ---------- helpers ----------

    private void markTrashed(FileNode node, boolean trashed) {
        Instant now = Instant.now();
        node.setTrashed(trashed);
        node.setTrashedAt(trashed ? now : null);
        node.setUpdatedAt(now);
        table.putItem(node);
        if (node.isFolder()) {
            for (FileNode child : childrenOf(node.getId())) {
                markTrashed(child, trashed);
            }
        }
    }

    private void purge(FileNode node) {
        if (node.isFolder()) {
            for (FileNode child : childrenOf(node.getId())) {
                purge(child);
            }
        } else if (node.getS3Key() != null) {
            s3.deleteObject(b -> b.bucket(props.bucket()).key(node.getS3Key()));
        }
        table.deleteItem(Key.builder().partitionValue(node.getId()).build());
    }

    private List<FileNode> childrenOf(String parentId) {
        return parentIndex
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(parentId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    private void requireFolder(String userId, String id) {
        FileNode node = get(userId, id);
        if (!node.isFolder()) {
            throw new NotFoundException("El recurso " + id + " no es una carpeta");
        }
    }

    private FileNode newNode(String userId, String name, String parentId) {
        FileNode node = new FileNode();
        Instant now = Instant.now();
        node.setId(UUID.randomUUID().toString());
        node.setOwnerId(userId);
        node.setName(name);
        node.setParentId(parentId);
        node.setTrashed(false);
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        return node;
    }

    private String resolveParent(String userId, String parentId) {
        if (parentId == null || parentId.isBlank() || parentId.equals("root")) {
            return FileNode.rootOf(userId);
        }
        return parentId;
    }

    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "archivo-sin-nombre" : name;
    }
}
