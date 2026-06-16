package io.drive.api.service;

import io.drive.api.config.AwsProperties;
import io.drive.api.exception.ConflictException;
import io.drive.api.exception.UnauthorizedException;
import io.drive.api.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final DynamoDbTable<User> table;
    private final DynamoDbIndex<User> usernameIndex;
    private final PasswordEncoder passwordEncoder;

    public UserService(DynamoDbEnhancedClient enhanced, AwsProperties props, PasswordEncoder passwordEncoder) {
        this.table = enhanced.table(props.usersTable(), TableSchema.fromBean(User.class));
        this.usernameIndex = table.index(User.USERNAME_INDEX);
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String rawPassword) {
        if (findByUsername(username) != null) {
            throw new ConflictException("El usuario '" + username + "' ya existe");
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setCreatedAt(Instant.now());
        table.putItem(user);
        return user;
    }

    public User authenticate(String username, String rawPassword) {
        User user = findByUsername(username);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Usuario o contrasena incorrectos");
        }
        return user;
    }

    private User findByUsername(String username) {
        return usernameIndex
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(username).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .orElse(null);
    }
}
