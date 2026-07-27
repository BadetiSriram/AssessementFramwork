package com.aaseya.camunda.framework.starter.data;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the behaviour of {@link FlywayNamingConventionValidator}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Valid migration filenames are accepted without exception.</li>
 *   <li>Invalid migration filenames cause a {@link FlywayException} to be thrown.</li>
 *   <li>Non-versioned filenames (not starting with {@code V}) are skipped.</li>
 *   <li>{@code supports()} returns {@code true} only for {@code BEFORE_MIGRATE}.</li>
 *   <li>{@code handle()} delegates to validation via the context's configuration.</li>
 * </ul>
 *
 * <p>Validation logic is tested via the package-private {@code validateFilenames()} method
 * to avoid the need for a live Flyway resource provider.
 */
class FlywayNamingConventionValidatorTest {

    private FlywayNamingConventionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FlywayNamingConventionValidator();
    }

    // -------------------------------------------------------------------------
    // supports() tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@code supports()} returns {@code true} for {@code BEFORE_MIGRATE}.
     */
    @Test
    void supports_returnsTrueForBeforeMigrate() {
        Context context = mock(Context.class);
        assertThat(validator.supports(Event.BEFORE_MIGRATE, context)).isTrue();
    }

    /**
     * Verifies that {@code supports()} returns {@code false} for {@code AFTER_MIGRATE}.
     */
    @Test
    void supports_returnsFalseForAfterMigrate() {
        Context context = mock(Context.class);
        assertThat(validator.supports(Event.AFTER_MIGRATE, context)).isFalse();
    }

    /**
     * Verifies that {@code canHandleInTransaction()} always returns {@code false}.
     */
    @Test
    void canHandleInTransaction_returnsFalse() {
        Context context = mock(Context.class);
        assertThat(validator.canHandleInTransaction(Event.BEFORE_MIGRATE, context)).isFalse();
    }

    // -------------------------------------------------------------------------
    // validateFilenames() — valid names
    // -------------------------------------------------------------------------

    /**
     * Verifies that well-formed versioned migration filenames are accepted without exception.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "V1__init.sql",
            "V2_3__add_index.sql",
            "V10_1_2__create_audit_table.sql",
            "V100__remove_old_column.sql",
            "V1__a.sql",
            "V1_0__some_migration_123.sql"
    })
    void validateFilenames_acceptsValidNames(String filename) {
        assertThatCode(() -> validator.validateFilenames(List.of(filename)))
                .doesNotThrowAnyException();
    }

    /**
     * Verifies that an empty list is accepted without exception.
     */
    @Test
    void validateFilenames_acceptsEmptyList() {
        assertThatCode(() -> validator.validateFilenames(List.of()))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // validateFilenames() — invalid names
    // -------------------------------------------------------------------------

    /**
     * Verifies that migration filenames with capital letters in the description are rejected.
     */
    @Test
    void validateFilenames_rejectsCapitalLetterInDescription() {
        assertThatThrownBy(() -> validator.validateFilenames(List.of("V1__Init.sql")))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V1__Init.sql");
    }

    /**
     * Verifies that migration filenames with camelCase descriptions are rejected.
     */
    @Test
    void validateFilenames_rejectsCamelCaseDescription() {
        assertThatThrownBy(() -> validator.validateFilenames(List.of("V1__addUserTable.sql")))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V1__addUserTable.sql");
    }

    /**
     * Verifies that migration filenames with spaces in the description are rejected.
     */
    @Test
    void validateFilenames_rejectsSpaceInDescription() {
        assertThatThrownBy(() -> validator.validateFilenames(List.of("V1__add index.sql")))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V1__add index.sql");
    }

    /**
     * Verifies that migration filenames with a lowercase version prefix are rejected.
     */
    @Test
    void validateFilenames_rejectsLowercaseVersionPrefix() {
        assertThatThrownBy(() -> validator.validateFilenames(List.of("v1__init.sql")))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("v1__init.sql");
    }

    /**
     * Verifies that migration filenames with a single underscore separator are rejected.
     */
    @Test
    void validateFilenames_rejectsSingleUnderscoreSeparator() {
        assertThatThrownBy(() -> validator.validateFilenames(List.of("V1_init.sql")))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V1_init.sql");
    }

    /**
     * Verifies that multiple violations are all reported in a single exception.
     */
    @Test
    void validateFilenames_reportsAllViolationsAtOnce() {
        List<String> filenames = List.of("V1__init.sql", "V2__BadName.sql", "V3__also_Bad.sql");
        assertThatThrownBy(() -> validator.validateFilenames(filenames))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V2__BadName.sql")
                .hasMessageContaining("V3__also_Bad.sql");
    }

    /**
     * Verifies that non-versioned filenames (repeatable migrations, etc.) are skipped
     * and do not trigger validation errors.
     */
    @Test
    void validateFilenames_skipsNonVersionedFilenames() {
        List<String> filenames = List.of("R__repeatable_migration.sql", "U1__undo.sql");
        assertThatCode(() -> validator.validateFilenames(filenames))
                .doesNotThrowAnyException();
    }

    /**
     * Verifies that a mix of valid and invalid versioned filenames causes rejection, while
     * valid filenames in the same batch are not mentioned in the exception.
     */
    @Test
    void validateFilenames_onlyRejectsInvalidOnes_inMixedList() {
        List<String> filenames = List.of(
                "V1__init.sql",           // valid
                "V2__BadName.sql",         // invalid — capital letter
                "V3__create_table.sql"     // valid
        );
        assertThatThrownBy(() -> validator.validateFilenames(filenames))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V2__BadName.sql")
                .hasMessageNotContaining("V1__init.sql")
                .hasMessageNotContaining("V3__create_table.sql");
    }

    // -------------------------------------------------------------------------
    // handle() with mocked context
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@code handle()} does not throw when the resource provider returns
     * {@code null} (i.e., when the configuration has no resource provider configured).
     */
    @Test
    void handle_doesNotThrow_whenResourceProviderIsNull() {
        Context context = mock(Context.class);
        Configuration config = mock(Configuration.class);
        when(context.getConfiguration()).thenReturn(config);
        when(config.getResourceProvider()).thenReturn(null);

        assertThatCode(() -> validator.handle(Event.BEFORE_MIGRATE, context))
                .doesNotThrowAnyException();
    }
}
