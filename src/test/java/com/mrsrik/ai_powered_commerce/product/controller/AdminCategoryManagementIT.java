package com.mrsrik.ai_powered_commerce.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.repository.CategoryRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * Validates admin category management (Requirement 2):
 * create, update, delete, 400 for blank and over-100 names, 409 duplicate name differing only
 * by case (including soft-deleted), 404 for unknown/deleted id, 409 category in use when active
 * products reference it, and deletable when only inactive or deleted products reference it.
 */
class AdminCategoryManagementIT extends AbstractWebIT {

    @Autowired
    private CategoryRepository categoryRepository;

    private static final String BASE_URL = "/api/categories";

    private String json(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    @Test
    void adminCreatesCategorySuccessfully() throws Exception {
        Caller admin = callers.registeredAdmin();

        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Electronics")))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name", is("Electronics")));

        Category saved = categoryRepository.findAll().stream()
                .filter(c -> "Electronics".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void adminUpdatesCategorySuccessfully() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category(b -> b.name("Old Name"));

        mockMvc.perform(put(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("New Name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(category.getId().toString())))
                .andExpect(jsonPath("$.name", is("New Name")));

        Category updated = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Name");

        // Updating with the same name on the same category succeeds (no self-conflict)
        mockMvc.perform(put(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("New Name")))
                .andExpect(status().isOk());
    }

    @Test
    void adminDeletesCategorySoftDeleteAndRepeatIs404() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category();

        // 1. Delete -> 204 No Content
        mockMvc.perform(delete(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        Category deleted = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();

        // Deleted category is hidden from public list
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 2. Repeat delete -> 404 CATEGORY_NOT_FOUND
        mockMvc.perform(delete(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CATEGORY_NOT_FOUND")));

        // 3. Update on deleted category -> 404 CATEGORY_NOT_FOUND
        mockMvc.perform(put(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Another Name")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void duplicateCategoryNameReturns409CaseInsensitiveAndSoftDeleted() throws Exception {
        Caller admin = callers.registeredAdmin();
        testData.category(b -> b.name("Laptops"));
        testData.category(b -> b.name("Monitors").deletedAt(Instant.now()));

        // Exact match on create -> 409
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Laptops")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_CATEGORY_NAME")));

        // Differing only by case -> 409
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("lApToPs")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_CATEGORY_NAME")));

        // Match with soft-deleted category -> 409
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("monitors")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_CATEGORY_NAME")));

        // Update collision with another category's name -> 409
        Category c3 = testData.category(b -> b.name("Keyboards"));
        mockMvc.perform(put(BASE_URL + "/" + c3.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("LAPTOPS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_CATEGORY_NAME")));
    }

    @Test
    void categoryInUseBlockedByActiveProductsOnly() throws Exception {
        Caller admin = callers.registeredAdmin();

        // 1. Category with active product -> 409 CATEGORY_IN_USE, category unchanged
        Category catWithActive = testData.category(b -> b.name("Active Cat"));
        testData.product(catWithActive.getId(), b -> b.active(true));

        mockMvc.perform(delete(BASE_URL + "/" + catWithActive.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CATEGORY_IN_USE")));

        assertThat(categoryRepository.findById(catWithActive.getId()).orElseThrow().getDeletedAt()).isNull();

        // 2. Category with only inactive products -> deletable (204)
        Category catWithInactive = testData.category(b -> b.name("Inactive Cat"));
        testData.product(catWithInactive.getId(), b -> b.active(false));

        mockMvc.perform(delete(BASE_URL + "/" + catWithInactive.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(catWithInactive.getId()).orElseThrow().getDeletedAt()).isNotNull();

        // 3. Category with only soft-deleted products -> deletable (204)
        Category catWithDeleted = testData.category(b -> b.name("Deleted Cat"));
        testData.product(catWithDeleted.getId(), b -> b.deletedAt(Instant.now()));

        mockMvc.perform(delete(BASE_URL + "/" + catWithDeleted.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(catWithDeleted.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void validationErrorsForBlankAndOversizedNames() throws Exception {
        Caller admin = callers.registeredAdmin();

        // Blank name -> 400 VALIDATION_FAILED
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("name")));

        // Name over 100 chars -> 400 VALIDATION_FAILED
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("A".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("name")));
    }

    @Test
    void roleGatingReturns401Or403ForNonAdmin() throws Exception {
        Caller customer = callers.registeredCustomer();
        Caller unregistered = callers.unregistered();
        Category category = testData.category();
        String body = json("Gated Category");

        // Anonymous -> 401
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(put(BASE_URL + "/" + category.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(delete(BASE_URL + "/" + category.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        // Customer -> 403 FORBIDDEN
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        mockMvc.perform(put(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        mockMvc.perform(delete(BASE_URL + "/" + category.getId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        // Unregistered -> 403 USER_NOT_REGISTERED
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, unregistered.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("USER_NOT_REGISTERED")));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        Caller admin = callers.registeredAdmin();
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(put(BASE_URL + "/" + unknownId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Some Name")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CATEGORY_NOT_FOUND")));

        mockMvc.perform(delete(BASE_URL + "/" + unknownId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        Caller admin = callers.registeredAdmin();

        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }
}

