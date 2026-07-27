package com.aaseya.camunda.framework.starter.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies the behaviour of {@link AuditColumnListener}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Pre-persist stamps {@code createdAt} and {@code updatedAt} when they are null.</li>
 *   <li>Pre-persist does not overwrite {@code createdAt} or {@code updatedAt} when already
 *       set.</li>
 *   <li>Pre-update always refreshes {@code updatedAt}.</li>
 *   <li>Pre-update does not modify {@code createdAt}.</li>
 *   <li>The listener is a no-op on POJOs that declare none of the four audit fields.</li>
 *   <li>The listener never throws regardless of entity structure.</li>
 * </ul>
 *
 * <p>Tests run without a Spring context; the listener is exercised directly as a plain Java
 * object to keep them fast and free of infrastructure dependencies.
 */
class AuditColumnListenerTest {

    private AuditColumnListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditColumnListener();
    }

    // -------------------------------------------------------------------------
    // Test entity with all four audit fields
    // -------------------------------------------------------------------------

    static class FullAuditEntity {
        Instant createdAt;
        Instant updatedAt;
        String  createdBy;
        String  updatedBy;
    }

    // -------------------------------------------------------------------------
    // Test entity with no audit fields
    // -------------------------------------------------------------------------

    static class NoAuditEntity {
        String name;
        int    value;
    }

    // -------------------------------------------------------------------------
    // Test entity with only timestamp fields (no String fields)
    // -------------------------------------------------------------------------

    static class TimestampOnlyEntity {
        Instant createdAt;
        Instant updatedAt;
    }

    // -------------------------------------------------------------------------
    // Pre-persist tests
    // -------------------------------------------------------------------------

    /**
     * When {@code createdAt} and {@code updatedAt} are null, {@code onPrePersist} must
     * populate them with a non-null {@link Instant}.
     */
    @Test
    void onPrePersist_setsCreatedAtAndUpdatedAt_whenNull() {
        FullAuditEntity entity = new FullAuditEntity();

        listener.onPrePersist(entity);

        assertThat(entity.createdAt).isNotNull();
        assertThat(entity.updatedAt).isNotNull();
    }

    /**
     * {@code createdAt} and {@code updatedAt} must be set to approximately the same
     * instant on pre-persist (within 1 second to account for clock calls).
     */
    @Test
    void onPrePersist_createdAtAndUpdatedAt_areCloseToNow() {
        FullAuditEntity entity = new FullAuditEntity();
        Instant before = Instant.now().minusSeconds(1);

        listener.onPrePersist(entity);

        Instant after = Instant.now().plusSeconds(1);
        assertThat(entity.createdAt).isAfter(before).isBefore(after);
        assertThat(entity.updatedAt).isAfter(before).isBefore(after);
    }

    /**
     * When {@code createdAt} is already set, {@code onPrePersist} must not overwrite it.
     */
    @Test
    void onPrePersist_doesNotOverwriteCreatedAt_whenAlreadySet() {
        FullAuditEntity entity = new FullAuditEntity();
        Instant existingCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        entity.createdAt = existingCreatedAt;

        listener.onPrePersist(entity);

        assertThat(entity.createdAt).isEqualTo(existingCreatedAt);
    }

    /**
     * When {@code updatedAt} is already set, {@code onPrePersist} must not overwrite it.
     */
    @Test
    void onPrePersist_doesNotOverwriteUpdatedAt_whenAlreadySet() {
        FullAuditEntity entity = new FullAuditEntity();
        Instant existingUpdatedAt = Instant.parse("2020-06-01T00:00:00Z");
        entity.updatedAt = existingUpdatedAt;

        listener.onPrePersist(entity);

        assertThat(entity.updatedAt).isEqualTo(existingUpdatedAt);
    }

    /**
     * {@code onPrePersist} must not throw on an entity with no audit fields.
     */
    @Test
    void onPrePersist_isNoOp_onEntityWithNoAuditFields() {
        NoAuditEntity entity = new NoAuditEntity();
        entity.name = "test";
        entity.value = 42;

        assertThatCode(() -> listener.onPrePersist(entity)).doesNotThrowAnyException();

        // Entity unchanged
        assertThat(entity.name).isEqualTo("test");
        assertThat(entity.value).isEqualTo(42);
    }

    /**
     * {@code onPrePersist} must not throw when passed a {@code null} object; it should
     * handle the exception internally.
     */
    @Test
    void onPrePersist_doesNotThrow_onNullEntity() {
        assertThatCode(() -> listener.onPrePersist(null)).doesNotThrowAnyException();
    }

    /**
     * {@code onPrePersist} on a timestamp-only entity must set both timestamp fields
     * and must not throw due to missing {@code createdBy}/{@code updatedBy}.
     */
    @Test
    void onPrePersist_setsTimestamps_onTimestampOnlyEntity() {
        TimestampOnlyEntity entity = new TimestampOnlyEntity();

        assertThatCode(() -> listener.onPrePersist(entity)).doesNotThrowAnyException();

        assertThat(entity.createdAt).isNotNull();
        assertThat(entity.updatedAt).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Pre-update tests
    // -------------------------------------------------------------------------

    /**
     * {@code onPreUpdate} must set {@code updatedAt} to a non-null instant regardless of
     * its prior value.
     */
    @Test
    void onPreUpdate_setsUpdatedAt_always() {
        FullAuditEntity entity = new FullAuditEntity();
        Instant originalCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        entity.createdAt = originalCreatedAt;
        entity.updatedAt = originalCreatedAt;

        listener.onPreUpdate(entity);

        assertThat(entity.updatedAt).isNotNull();
    }

    /**
     * {@code onPreUpdate} must not modify {@code createdAt}.
     */
    @Test
    void onPreUpdate_doesNotModifyCreatedAt() {
        FullAuditEntity entity = new FullAuditEntity();
        Instant originalCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        entity.createdAt = originalCreatedAt;

        listener.onPreUpdate(entity);

        assertThat(entity.createdAt).isEqualTo(originalCreatedAt);
    }

    /**
     * {@code onPreUpdate} must refresh {@code updatedAt} even when it already had a value.
     */
    @Test
    void onPreUpdate_refreshesUpdatedAt_whenAlreadySet() throws InterruptedException {
        FullAuditEntity entity = new FullAuditEntity();
        Instant oldUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        entity.updatedAt = oldUpdatedAt;

        listener.onPreUpdate(entity);

        assertThat(entity.updatedAt).isAfterOrEqualTo(oldUpdatedAt);
    }

    /**
     * {@code onPreUpdate} must not throw on an entity with no audit fields.
     */
    @Test
    void onPreUpdate_isNoOp_onEntityWithNoAuditFields() {
        NoAuditEntity entity = new NoAuditEntity();
        entity.name = "test";

        assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();

        assertThat(entity.name).isEqualTo("test");
    }

    /**
     * {@code onPreUpdate} must not throw when passed a {@code null} object.
     */
    @Test
    void onPreUpdate_doesNotThrow_onNullEntity() {
        assertThatCode(() -> listener.onPreUpdate(null)).doesNotThrowAnyException();
    }
}
