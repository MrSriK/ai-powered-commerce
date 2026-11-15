package com.mrsrik.ai_powered_commerce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.mrsrik.mappingfixture.DuplicateColumnFixture;

import jakarta.persistence.Entity;

/**
 * Database-free check of the Hibernate mapping (decisions.md O-6, D-48).
 *
 * Builds the metadata and a SessionFactory for every {@code @Entity} found
 * under
 * {@code com.mrsrik.ai_powered_commerce}, with the PostgreSQL dialect and JDBC
 * metadata
 * access disabled. No Spring context, no Testcontainers, no connection.
 * Building the
 * SessionFactory is what surfaces errors such as
 * "Column 'user_id' is duplicated in mapping for entity ...".
 */
class MappingSmokeTest {

    private static final String BASE_PACKAGE = "com.mrsrik.ai_powered_commerce";

    private static final Set<String> EXPECTED_ENTITIES = Set.of(
            "User", "Address", "Category", "Product", "ProductImage", "Cart", "CartItem",
            "Order", "OrderItem", "AiConversation", "AiMessage");

    @Test
    void allDiscoveredEntitiesBuildAValidMapping() throws ClassNotFoundException {
        List<Class<?>> entities = discoverEntities(BASE_PACKAGE);

        Set<String> simpleNames = entities.stream().map(Class::getSimpleName).collect(Collectors.toSet());
        assertTrue(simpleNames.containsAll(EXPECTED_ENTITIES),
                "Entity scan is missing expected entities; found " + simpleNames);
        assertTrue(entities.size() >= EXPECTED_ENTITIES.size(),
                "Expected at least " + EXPECTED_ENTITIES.size() + " entities, found " + entities.size());

        buildSessionFactory(entities).close();
    }

    /** Proves the check can fail: a deliberately broken entity must be rejected. */
    @Test
    void duplicateColumnMappingIsRejected() {
        Exception failure = assertThrows(Exception.class,
                () -> buildSessionFactory(List.of(DuplicateColumnFixture.class)).close());

        assertTrue(messages(failure).contains("duplicated in mapping"),
                "Expected a duplicate-column error but got: " + messages(failure));
    }

    private static List<Class<?>> discoverEntities(String basePackage) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> entities = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents(basePackage)) {
            entities.add(Class.forName(definition.getBeanClassName()));
        }
        entities.sort(Comparator.comparing(Class::getName));
        return entities;
    }

    private static SessionFactory buildSessionFactory(List<Class<?>> entities) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            entities.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();
            return metadata.buildSessionFactory();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }

    private static String messages(Throwable throwable) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
