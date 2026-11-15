package com.mrsrik.ai_powered_commerce.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.ai.provider.AiReply;
import com.mrsrik.ai_powered_commerce.ai.provider.AiRequest;
import com.mrsrik.ai_powered_commerce.ai.provider.AiTurn;
import com.mrsrik.ai_powered_commerce.ai.service.PromptBuilder;
import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;
import com.mrsrik.ai_powered_commerce.product.service.ProductSearchService;
import com.mrsrik.ai_powered_commerce.security.LocalUserJwtAuthenticationConverter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

/**
 * Architecture rules verifying package boundaries, API shape, and data management constraints
 * (Task 38, Requirements 7.10, 7.9, 8.1, 6.1, 1.13, 7.3, 10.2).
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mrsrik.ai_powered_commerce");

    // =========================================================================
    // 38.1 Package Boundaries
    // =========================================================================

    @Test
    @DisplayName("AI package imports only ProductSearchService and ProductSearchResult from product package")
    void aiPackageImportsOnlyAllowedProductClasses() {
        ArchRule rule = classes()
                .that().resideInAPackage("..ai..")
                .should().onlyDependOnClassesThat(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage("..product..")
                                .or(type(ProductSearchService.class))
                                .or(type(ProductSearchResult.class)));

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("No class in AI package uses product repositories or entities")
    void aiPackageNeverUsesProductRepositoriesOrEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..ai..")
                .should().dependOnClassesThat(resideInAnyPackage("..product.entity..", "..product.repository.."));

        rule.check(PRODUCTION_CLASSES);
    }

    // =========================================================================
    // 38.2 API Shape Checks
    // =========================================================================

    @Test
    @DisplayName("Controller methods return no JPA @Entity types directly or in generics")
    void controllerMethodsNeverReturnEntityTypes() {
        for (var javaClass : PRODUCTION_CLASSES) {
            if (javaClass.isAnnotatedWith(RestController.class)) {
                Class<?> clazz = javaClass.reflect();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                        assertNotEntity(method.getReturnType(), method);
                        assertGenericTypesNotEntity(method.getGenericReturnType(), method);
                    }
                }
            }
        }
    }

    private void assertNotEntity(Class<?> clazz, Method method) {
        if (clazz == null || clazz.equals(void.class)) {
            return;
        }
        assertThat(clazz.isAnnotationPresent(Entity.class))
                .withFailMessage("Controller method %s returns JPA Entity %s directly", method, clazz)
                .isFalse();
        assertThat(clazz.getPackageName())
                .withFailMessage("Controller method %s returns type from entity package %s", method, clazz)
                .doesNotContain(".entity");
    }

    private void assertGenericTypesNotEntity(Type type, Method method) {
        if (type instanceof ParameterizedType pt) {
            for (Type arg : pt.getActualTypeArguments()) {
                if (arg instanceof Class<?> argClass) {
                    assertNotEntity(argClass, method);
                } else if (arg instanceof ParameterizedType nestedPt) {
                    assertGenericTypesNotEntity(nestedPt, method);
                }
            }
        }
    }

    @Test
    @DisplayName("ProductSearchResult and AI prompt classes have no costPrice component or reference")
    void aiPromptClassesHaveNoCostPrice() {
        List<Class<?>> promptClasses = List.of(
                ProductSearchResult.class,
                PromptBuilder.class,
                AiRequest.class,
                AiTurn.class,
                AiReply.class);

        for (Class<?> clazz : promptClasses) {
            if (clazz.isRecord()) {
                for (RecordComponent rc : clazz.getRecordComponents()) {
                    assertThat(rc.getName().toLowerCase())
                            .withFailMessage("Record %s has costPrice component: %s", clazz.getSimpleName(), rc.getName())
                            .doesNotContain("costprice");
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                assertThat(field.getName().toLowerCase())
                        .withFailMessage("Class %s has costPrice field: %s", clazz.getSimpleName(), field.getName())
                        .doesNotContain("costprice");
            }
            for (Method method : clazz.getDeclaredMethods()) {
                assertThat(method.getName().toLowerCase())
                        .withFailMessage("Class %s has costPrice method: %s", clazz.getSimpleName(), method.getName())
                        .doesNotContain("costprice");
            }
        }
    }

    @Test
    @DisplayName("LocalUserJwtAuthenticationConverter depends on no repository write methods")
    void authenticationConverterDependsOnNoRepositoryWriteMethods() {
        for (Method method : LocalUserJwtAuthenticationConverter.class.getDeclaredMethods()) {
            assertThat(method.getName().toLowerCase())
                    .doesNotStartWith("save")
                    .doesNotStartWith("delete")
                    .doesNotStartWith("update");
        }

        // Verify with ArchUnit that it only invokes read methods on UserRepository
        ArchRule rule = noClasses()
                .that().haveFullyQualifiedName(LocalUserJwtAuthenticationConverter.class.getName())
                .should().callMethodWhere(
                        com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching("save.*|delete.*|update.*")));

        rule.check(PRODUCTION_CLASSES);
    }

    // =========================================================================
    // 38.3 Source Scan for Database-Managed Columns & Foreign Keys
    // =========================================================================

    @Test
    @DisplayName("Production code never calls setCreatedAt or setUpdatedAt or builder methods for them")
    void productionCodeNeverSetsCreatedAtOrUpdatedAt() throws IOException {
        Path srcMain = Paths.get("src/main/java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            for (int i = 0; i < lines.size(); i++) {
                                String line = lines.get(i).trim();
                                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                                    continue;
                                }
                                if (line.contains("setCreatedAt(") || line.contains("setUpdatedAt(")
                                        || line.contains(".createdAt(") || line.contains(".updatedAt(")) {
                                    violations.add(path.getFileName() + ":" + (i + 1) + ": " + line);
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertThat(violations)
                .withFailMessage("Production code sets database-managed timestamp columns: %s", violations)
                .isEmpty();
    }

    @Test
    @DisplayName("Service creation code sets scalar FK ids when creating entities")
    void serviceEntityCreationAlwaysSetsScalarForeignKeys() throws IOException {
        Path serviceDir = Paths.get("src/main/java");
        List<String> violations = new ArrayList<>();

        // Entities with scalar FK and corresponding association:
        // CartItem: cartId, productId
        // OrderItem: orderId, productId
        // Order: userId
        // Cart: userId
        // Product: categoryId
        // AiConversation: userId
        // AiMessage: conversationId

        Pattern associationWithoutScalarOrder = Pattern.compile("\\.order\\((?!.*\\.orderId\\()");
        Pattern associationWithoutScalarCart = Pattern.compile("\\.cart\\((?!.*\\.cartId\\()");

        try (Stream<Path> paths = Files.walk(serviceDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("/service/"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            // If code calls .user( but not .userId( when building Order or Cart or AiConversation
                            if (content.contains("Order.builder()") && content.contains(".user(") && !content.contains(".userId(")) {
                                violations.add(path.getFileName() + " sets user association without setting userId scalar FK");
                            }
                            if (content.contains("Cart.builder()") && content.contains(".user(") && !content.contains(".userId(")) {
                                violations.add(path.getFileName() + " sets user association without setting userId scalar FK");
                            }
                            if (content.contains("CartItem.builder()") && (content.contains(".cart(") || content.contains(".product("))) {
                                if (!content.contains(".cartId(") || !content.contains(".productId(")) {
                                    violations.add(path.getFileName() + " builds CartItem without setting cartId or productId scalar FK");
                                }
                            }
                            if (content.contains("OrderItem.builder()") && content.contains(".order(") && !content.contains(".orderId(")) {
                                violations.add(path.getFileName() + " builds OrderItem without setting orderId scalar FK");
                            }
                            if (content.contains("AiConversation.builder()") && content.contains(".user(") && !content.contains(".userId(")) {
                                violations.add(path.getFileName() + " builds AiConversation without setting userId scalar FK");
                            }
                            if (content.contains("AiMessage.builder()") && content.contains(".aiConversation(") && !content.contains(".conversationId(")) {
                                violations.add(path.getFileName() + " builds AiMessage without setting conversationId scalar FK");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertThat(violations)
                .withFailMessage("Service creation code omits scalar foreign key ids: %s", violations)
                .isEmpty();
    }
}
