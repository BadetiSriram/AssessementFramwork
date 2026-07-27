package com.aaseya.camunda.framework.starter.data;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.time.Instant;

/**
 * JPA entity listener that reflectively stamps standard audit columns on entities
 * before they are persisted or updated.
 *
 * <p>Consumers register this listener on their entity classes:
 * <pre>{@code
 * @Entity
 * @EntityListeners(AuditColumnListener.class)
 * public class MyEntity {
 *     private Instant createdAt;
 *     private Instant updatedAt;
 *     private String  createdBy;
 *     private String  updatedBy;
 *     // ...
 * }
 * }</pre>
 *
 * <p>The listener supports the following field names and types:
 * <ul>
 *   <li>{@code createdAt} — {@link Instant}; set on {@code @PrePersist} if {@code null}</li>
 *   <li>{@code updatedAt} — {@link Instant}; set on {@code @PrePersist} if {@code null},
 *       and always refreshed on {@code @PreUpdate}</li>
 *   <li>{@code createdBy} — {@link String}; set on {@code @PrePersist} from the current
 *       HTTP request header (best-effort)</li>
 *   <li>{@code updatedBy} — {@link String}; set on every {@code @PrePersist} and
 *       {@code @PreUpdate} from the current HTTP request header (best-effort)</li>
 * </ul>
 *
 * <p>All reflection is defensive: fields that do not exist on the entity are silently
 * skipped, and any exception is caught and logged at DEBUG level so that the listener
 * never interrupts the JPA lifecycle. Entities that declare none of the four fields
 * receive a no-op.
 *
 * <p>The user identity is read from the HTTP request header configured via
 * {@code framework.data.audit.created-by-header} (default {@code X-User-Id}). If the
 * listener is invoked outside an active HTTP request context (e.g., in a batch or worker
 * thread), the identity fields are left {@code null}.
 */
public class AuditColumnListener {

    private static final Logger log = LoggerFactory.getLogger(AuditColumnListener.class);

    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_CREATED_BY = "createdBy";
    private static final String FIELD_UPDATED_BY = "updatedBy";
    private static final String DEFAULT_USER_HEADER = "X-User-Id";

    /**
     * Invoked by the JPA provider before a new entity is inserted.
     *
     * <p>Sets {@code createdAt} and {@code updatedAt} to {@link Instant#now()} when they
     * are {@code null}, and populates {@code createdBy} / {@code updatedBy} from the
     * current HTTP request header when available.
     *
     * @param entity the entity about to be persisted; never {@code null}
     */
    @PrePersist
    public void onPrePersist(Object entity) {
        if (entity == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            setFieldIfNullAndInstant(entity, FIELD_CREATED_AT, now);
            setFieldIfNullAndInstant(entity, FIELD_UPDATED_AT, now);

            String userId = resolveUserId();
            setStringFieldIfPresent(entity, FIELD_CREATED_BY, userId);
            setStringFieldIfPresent(entity, FIELD_UPDATED_BY, userId);
        } catch (Exception ex) {
            log.debug("AuditColumnListener.onPrePersist failed for entity {}: {}",
                    entity.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    /**
     * Invoked by the JPA provider before an existing entity is updated.
     *
     * <p>Always refreshes {@code updatedAt} to {@link Instant#now()} and updates
     * {@code updatedBy} from the current HTTP request header when available.
     *
     * @param entity the entity about to be updated; never {@code null}
     */
    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (entity == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            setFieldValue(entity, FIELD_UPDATED_AT, now, Instant.class);

            String userId = resolveUserId();
            setStringFieldIfPresent(entity, FIELD_UPDATED_BY, userId);
        } catch (Exception ex) {
            log.debug("AuditColumnListener.onPreUpdate failed for entity {}: {}",
                    entity.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    /**
     * Sets an {@link Instant} field to the given value only if the field currently holds
     * {@code null}. Silently skips when the field does not exist on the entity.
     *
     * @param entity    the entity on which to set the field
     * @param fieldName the name of the field to set
     * @param value     the value to assign
     */
    private void setFieldIfNullAndInstant(Object entity, String fieldName, Instant value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field == null) {
                return;
            }
            if (!Instant.class.isAssignableFrom(field.getType())) {
                return;
            }
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, value);
            }
        } catch (Exception ex) {
            log.debug("Could not set {} on {}: {}", fieldName,
                    entity.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    /**
     * Sets an {@link Instant} field unconditionally. Silently skips when the field does
     * not exist on the entity.
     *
     * @param entity     the entity on which to set the field
     * @param fieldName  the name of the field to set
     * @param value      the value to assign
     * @param fieldType  the expected field type (used for type-safety check)
     */
    private void setFieldValue(Object entity, String fieldName, Object value,
                                Class<?> fieldType) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field == null) {
                return;
            }
            if (!fieldType.isAssignableFrom(field.getType())) {
                return;
            }
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception ex) {
            log.debug("Could not set {} on {}: {}", fieldName,
                    entity.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    /**
     * Sets a {@link String} field to the given value when the field exists and the value
     * is non-{@code null}. Silently skips when the field does not exist.
     *
     * @param entity    the entity on which to set the field
     * @param fieldName the name of the field to set
     * @param value     the string value to assign; if {@code null}, the field is not modified
     */
    private void setStringFieldIfPresent(Object entity, String fieldName, String value) {
        if (value == null) {
            return;
        }
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field == null) {
                return;
            }
            if (!String.class.isAssignableFrom(field.getType())) {
                return;
            }
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception ex) {
            log.debug("Could not set {} on {}: {}", fieldName,
                    entity.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }

    /**
     * Attempts to read the user identity from the current HTTP request context.
     *
     * <p>Uses {@link RequestContextHolder} to obtain the current request attributes. If
     * no request context is bound (e.g., the method is called from a background thread),
     * returns {@code null} without throwing. All exceptions are swallowed at DEBUG level.
     *
     * @return the value of the {@code X-User-Id} header, or {@code null} if unavailable
     */
    private String resolveUserId() {
        try {
            var attributes = RequestContextHolder.currentRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return servletAttributes.getRequest().getHeader(DEFAULT_USER_HEADER);
            }
        } catch (Exception ex) {
            log.debug("Could not resolve user identity from request context: {}", ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * Searches the given class and its superclass hierarchy for a declared field with the
     * specified name.
     *
     * @param clazz     the class to inspect; never {@code null}
     * @param fieldName the name of the field to locate
     * @return the {@link Field}, or {@code null} if not found in the hierarchy
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
