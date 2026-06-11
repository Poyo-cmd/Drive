package io.drive.api.service;

import io.drive.api.config.AwsProperties;
import io.drive.api.model.FileNode;
import io.drive.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Provisiona la infraestructura necesaria (bucket + tablas) contra Floci en el
 * arranque. En AWS real esto lo haria Terraform/CDK, pero para desarrollo local
 * es comodo dejarlo idempotente aqui.
 */
@Component
public class StorageBootstrap {

    private static final Logger log = LoggerFactory.getLogger(StorageBootstrap.class);

    private final S3Client s3;
    private final DynamoDbClient dynamo;
    private final AwsProperties props;

    public StorageBootstrap(S3Client s3, DynamoDbClient dynamo, AwsProperties props) {
        this.s3 = s3;
        this.dynamo = dynamo;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void provision() {
        // Floci puede tardar un poco en levantar dentro de docker-compose,
        // asi que reintentamos hasta que responda (en vez de gatillar un healthcheck fragil).
        int maxAttempts = 30;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ensureBucket();
                ensureNodesTable();
                ensureUsersTable();
                log.info("Infraestructura lista (intento {})", attempt);
                return;
            } catch (Exception e) {
                log.warn("Floci aun no responde (intento {}/{}): {}", attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("No se pudo provisionar la infraestructura tras {} intentos", maxAttempts);
    }

    private void ensureBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            log.info("Bucket '{}' creado", props.bucket());
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("Bucket '{}' ya existe", props.bucket());
        }
    }

    private void ensureNodesTable() {
        try {
            dynamo.createTable(builder -> builder
                    .tableName(props.metadataTable())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("parentId").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName(FileNode.PARENT_INDEX)
                            .keySchema(KeySchemaElement.builder().attributeName("parentId").keyType(KeyType.HASH).build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()));
            log.info("Tabla '{}' creada", props.metadataTable());
        } catch (ResourceInUseException e) {
            log.info("Tabla '{}' ya existe", props.metadataTable());
        }
    }

    private void ensureUsersTable() {
        try {
            dynamo.createTable(builder -> builder
                    .tableName(props.usersTable())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("username").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName(User.USERNAME_INDEX)
                            .keySchema(KeySchemaElement.builder().attributeName("username").keyType(KeyType.HASH).build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build()));
            log.info("Tabla '{}' creada", props.usersTable());
        } catch (ResourceInUseException e) {
            log.info("Tabla '{}' ya existe", props.usersTable());
        }
    }
}
