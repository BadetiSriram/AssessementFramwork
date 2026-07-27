package com.aaseya.camunda.framework.core.audit;

import com.aaseya.camunda.framework.core.exception.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link AuditableEntity} enforces state machine rules and appends audit
 * notes on every legal transition, while rejecting illegal transitions with an exception.
 */
class AuditableEntityTest {

    enum BookingStatus { PENDING, CONFIRMED, CANCELLED }

    /** Minimal concrete entity that tracks status and audit notes in memory. */
    static class TestBooking extends AuditableEntity<BookingStatus> {

        private BookingStatus status = BookingStatus.PENDING;
        private final List<String> auditNotes = new ArrayList<>();

        @Override
        protected Set<BookingStatus> allowedTransitions(BookingStatus from) {
            return switch (from) {
                case PENDING   -> EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
                case CONFIRMED -> EnumSet.of(BookingStatus.CANCELLED);
                case CANCELLED -> EnumSet.noneOf(BookingStatus.class);
            };
        }

        @Override
        protected BookingStatus getStatus() {
            return status;
        }

        @Override
        protected void setStatus(BookingStatus status) {
            this.status = status;
        }

        @Override
        protected void appendAuditNote(String note, BookingStatus from, BookingStatus to) {
            auditNotes.add(from.name() + "->" + to.name() + ": " + note);
        }

        List<String> getAuditNotes() {
            return auditNotes;
        }
    }

    @Test
    void legalTransition_updatesStatusAndAppendsAuditNote() {
        TestBooking booking = new TestBooking();

        booking.transition(BookingStatus.CONFIRMED, "Payment received");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getAuditNotes())
                .hasSize(1)
                .containsExactly("PENDING->CONFIRMED: Payment received");
    }

    @Test
    void multipleTransitions_buildCorrectAuditTrail() {
        TestBooking booking = new TestBooking();

        booking.transition(BookingStatus.CONFIRMED, "Step 1");
        booking.transition(BookingStatus.CANCELLED, "Customer cancelled");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getAuditNotes()).hasSize(2);
        assertThat(booking.getAuditNotes().get(1)).isEqualTo("CONFIRMED->CANCELLED: Customer cancelled");
    }

    @Test
    void illegalTransition_throwsIllegalStateTransitionException() {
        TestBooking booking = new TestBooking();
        booking.transition(BookingStatus.CANCELLED, "Early cancel");

        // CANCELLED → CONFIRMED is not allowed
        assertThatThrownBy(() -> booking.transition(BookingStatus.CONFIRMED, "Reopen"))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void illegalTransition_doesNotChangeStatus() {
        TestBooking booking = new TestBooking();

        try {
            booking.transition(BookingStatus.CANCELLED, "First");
            booking.transition(BookingStatus.CONFIRMED, "Illegal");
        } catch (IllegalStateTransitionException ignored) {
            // expected
        }

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Only the first (legal) transition audit note should be present
        assertThat(booking.getAuditNotes()).hasSize(1);
    }

    @Test
    void illegalStateTransitionException_carriesFromAndToStates() {
        TestBooking booking = new TestBooking();
        booking.transition(BookingStatus.CANCELLED, "cancel");

        assertThatThrownBy(() -> booking.transition(BookingStatus.PENDING, "reopen"))
                .isInstanceOf(IllegalStateTransitionException.class)
                .satisfies(ex -> {
                    IllegalStateTransitionException iste = (IllegalStateTransitionException) ex;
                    assertThat(iste.getFromState()).isEqualTo("CANCELLED");
                    assertThat(iste.getToState()).isEqualTo("PENDING");
                });
    }
}
