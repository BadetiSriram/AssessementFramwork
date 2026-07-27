package com.aaseya.camunda.framework.core.worker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Anti-corruption layer between raw Camunda job variables (JSON strings / {@code Map})
 * and typed Java records used by each worker.  Centralises all Jackson configuration so
 * that BPMN wire names appear only here, never scattered across worker classes.
 */
public class VariableMapper {

    private final ObjectMapper mapper;

    /**
     * Constructor-injection entry point for Spring-managed contexts.
     * The supplied mapper is used as-is; callers should configure it appropriately
     * (see {@link #createDefault()} for the recommended baseline).
     *
     * @param mapper pre-configured Jackson {@code ObjectMapper}
     */
    public VariableMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Creates a {@link VariableMapper} with a sensible default {@link ObjectMapper}:
     * {@code FAIL_ON_UNKNOWN_PROPERTIES=false}, {@link JavaTimeModule} registered, and
     * {@code NON_NULL} serialization.  Use this factory in unit tests or when no Spring
     * context is available.
     *
     * @return a ready-to-use {@code VariableMapper}
     */
    public static VariableMapper createDefault() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.registerModule(new JavaTimeModule());
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return new VariableMapper(om);
    }

    /**
     * Deserializes a {@code Map} of raw process variables into the target record type.
     * After binding, validates that all non-{@code @Nullable} record components are
     * non-{@code null}; throws {@link VariableBindingException} on any violation.
     *
     * @param <T>  target record type
     * @param vars raw variable map from the Camunda job
     * @param type class token for the target type
     * @return a populated instance of {@code T}
     * @throws VariableBindingException if a required component is missing or null
     */
    public <T> T map(Map<String, Object> vars, Class<T> type) {
        try {
            T instance = mapper.convertValue(vars, type);
            validateRequiredComponents(instance, type);
            return instance;
        } catch (VariableBindingException e) {
            throw e;
        } catch (Exception e) {
            throw new VariableBindingException(
                    "Failed to bind variables to " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a JSON string of process variables into the target record type.
     * Delegates to {@link #map(Map, Class)} after parsing the JSON into a {@code Map}.
     *
     * @param <T>     target record type
     * @param json    raw JSON variable payload (e.g. from {@code ActivatedJob.getVariables()})
     * @param type    class token for the target type
     * @return a populated instance of {@code T}
     * @throws VariableBindingException if parsing or required-component validation fails
     */
    @SuppressWarnings("unchecked")
    public <T> T map(String json, Class<T> type) {
        try {
            Map<String, Object> vars = mapper.readValue(json, Map.class);
            return map(vars, type);
        } catch (VariableBindingException e) {
            throw e;
        } catch (Exception e) {
            throw new VariableBindingException(
                    "Failed to parse variables JSON for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Serializes an object (typically a result-variable record) into a plain
     * {@code Map<String, Object>} that Camunda accepts as job completion variables.
     *
     * @param obj source object; must be Jackson-serializable
     * @return a {@code Map} representation suitable for {@code newCompleteCommand().variables(...)}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(Object obj) {
        return mapper.convertValue(obj, Map.class);
    }

    /**
     * Checks that every record component (which represents a required field by convention)
     * is non-null after deserialization.  Components annotated with any annotation whose
     * simple name is {@code Nullable} are exempt.
     */
    private <T> void validateRequiredComponents(T instance, Class<T> type) {
        if (!type.isRecord()) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            boolean isNullable = java.util.Arrays.stream(component.getAnnotations())
                    .anyMatch(a -> "Nullable".equals(a.annotationType().getSimpleName()));
            if (!isNullable) {
                try {
                    Object value = component.getAccessor().invoke(instance);
                    if (value == null) {
                        throw new VariableBindingException(
                                "Required variable '" + component.getName()
                                + "' is null in " + type.getSimpleName()
                                + " — check that the BPMN process supplies this variable.");
                    }
                } catch (VariableBindingException e) {
                    throw e;
                } catch (Exception e) {
                    throw new VariableBindingException(
                            "Could not access component '" + component.getName()
                            + "' of " + type.getSimpleName(), e);
                }
            }
        }
    }
}
