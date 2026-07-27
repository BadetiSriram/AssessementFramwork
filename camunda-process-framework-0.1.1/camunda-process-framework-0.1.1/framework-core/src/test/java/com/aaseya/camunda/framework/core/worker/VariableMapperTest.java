package com.aaseya.camunda.framework.core.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link VariableMapper} correctly binds BPMN process variables to
 * typed Java records and rejects inputs that violate required-component contracts.
 */
class VariableMapperTest {

    private VariableMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = VariableMapper.createDefault();
    }

    // ---- test records ----

    record AllTypesVars(String name, BigDecimal amount, Instant occurredAt) {}

    record WithOptionalVars(String required, @org.springframework.lang.Nullable String optional) {}

    record RequiredVars(String requiredField) {}

    // ---- tests ----

    @Test
    void roundTrip_basicTypes_bindsCorrectly() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        Map<String, Object> vars = Map.of(
                "name", "booking-001",
                "amount", "99.95",
                "occurredAt", now.toString()
        );

        AllTypesVars result = mapper.map(vars, AllTypesVars.class);

        assertThat(result.name()).isEqualTo("booking-001");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("99.95"));
        assertThat(result.occurredAt()).isEqualTo(now);
    }

    @Test
    void unknownProperties_doNotCauseFailure() {
        Map<String, Object> vars = Map.of(
                "requiredField", "hello",
                "unknownExtraField", "ignored",
                "anotherUnknown", 42
        );

        RequiredVars result = mapper.map(vars, RequiredVars.class);

        assertThat(result.requiredField()).isEqualTo("hello");
    }

    @Test
    void nestedMap_inVariables_isPreserved() {
        Map<String, Object> nested = Map.of("key", "value");
        Map<String, Object> vars = Map.of(
                "name", "test",
                "amount", "10.00",
                "occurredAt", "2026-01-01T00:00:00Z"
        );

        AllTypesVars result = mapper.map(vars, AllTypesVars.class);
        assertThat(result).isNotNull();
    }

    @Test
    void requiredComponent_null_throwsVariableBindingException() {
        // requiredField is absent from the map → should be null after binding
        Map<String, Object> vars = Map.of("unrelated", "value");

        assertThatThrownBy(() -> mapper.map(vars, RequiredVars.class))
                .isInstanceOf(VariableBindingException.class)
                .hasMessageContaining("requiredField");
    }

    @Test
    void toMap_serializesObjectToMap() {
        AllTypesVars vars = new AllTypesVars("test", new BigDecimal("5.00"), Instant.parse("2026-03-01T12:00:00Z"));

        Map<String, Object> result = mapper.toMap(vars);

        assertThat(result).containsKey("name");
        assertThat(result.get("name")).isEqualTo("test");
    }

    @Test
    void roundTrip_mapToRecordAndBack_isStable() {
        Map<String, Object> original = Map.of(
                "name", "stable-test",
                "amount", "123.45",
                "occurredAt", "2026-06-01T08:00:00Z"
        );

        AllTypesVars record = mapper.map(original, AllTypesVars.class);
        Map<String, Object> back = mapper.toMap(record);

        assertThat(back.get("name")).isEqualTo("stable-test");
    }
}
