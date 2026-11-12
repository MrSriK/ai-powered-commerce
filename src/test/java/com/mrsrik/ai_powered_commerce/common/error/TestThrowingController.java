package com.mrsrik.ai_powered_commerce.common.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller whose endpoints raise the exceptions under test. */
@RestController
@RequestMapping("/t")
class TestThrowingController {

    private final Validator validator;

    TestThrowingController(Validator validator) {
        this.validator = validator;
    }

    enum Color { RED, GREEN }

    record Payload(@NotBlank String name, @Positive Integer price, @Size(min = 3) String sku) {
    }

    @GetMapping("/api/{kind}")
    void api(@PathVariable String kind) {
        switch (kind) {
            case "notfound" -> throw new ProductNotFoundException();
            case "duplicate" -> throw new DuplicateSkuException();
            case "stock" -> throw new InsufficientStockException();
            case "ai" -> throw new AiProviderException();
            default -> throw new InvalidPaginationException();
        }
    }

    @GetMapping("/uuid/{id}")
    String uuid(@PathVariable UUID id) {
        return id.toString();
    }

    @GetMapping("/enum")
    String color(@RequestParam Color color) {
        return color.name();
    }

    @GetMapping("/param")
    String requiredParam(@RequestParam("q") String q) {
        return q;
    }

    @GetMapping("/size")
    String size(@RequestParam @Min(1) int size, @RequestParam @Min(0) int page) {
        return size + ":" + page;
    }

    @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
    String body(@Valid @RequestBody Payload payload) {
        return payload.name();
    }

    @GetMapping("/constraint")
    void constraint() {
        throw new ConstraintViolationException(validator.validate(new Payload("", -1, "a")));
    }

    @GetMapping("/denied")
    void denied() {
        throw new AccessDeniedException("method security says no");
    }

    @GetMapping("/integrity")
    void integrity() {
        throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_products_sku\"");
    }

    @GetMapping("/boom")
    void boom() {
        throw new RuntimeException("jdbc:postgresql://secret-host password=hunter2");
    }
}
