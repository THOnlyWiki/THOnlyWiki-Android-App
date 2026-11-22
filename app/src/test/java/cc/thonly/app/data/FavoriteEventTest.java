package cc.thonly.app.data;

import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoriteEventTest {

    @Test
    void dateChangeKeepsPreviousDateAndUsesNewCalendarDay() {
        FavoriteEvent saturday = scheduled();
        FavoriteEvent sunday = saturday.updated("活动", "上海市", "2026-11-29", EventStatus.CONFIRMED);

        assertEquals("2026-11-28", sunday.previousDate());
        assertEquals(Calendar.SUNDAY, sunday.calendar().get(Calendar.DAY_OF_WEEK));
        assertSame(sunday, sunday.updated("活动", "上海市", "2026-11-29", EventStatus.CONFIRMED));
    }

    @Test
    void cancellationAndPostponementStopRemindersUntilConfirmedAgain() {
        FavoriteEvent original = scheduled();
        FavoriteEvent postponed = original.updated("活动", "上海市", original.rawDate(), EventStatus.POSTPONED);
        FavoriteEvent cancelled = postponed.updated("活动", "上海市", original.rawDate(), EventStatus.CANCELLED);
        FavoriteEvent confirmed = cancelled.updated("活动", "上海市", "2026-12-05", EventStatus.CONFIRMED);

        assertTrue(original.canRemind());
        assertFalse(postponed.canRemind());
        assertFalse(cancelled.canRemind());
        assertNull(cancelled.previousDate());
        assertTrue(confirmed.canRemind());
        assertEquals(original.rawDate(), confirmed.previousDate());
    }

    @Test
    void removingAndRestoringAnActivityPreservesTheSavedSnapshot() {
        FavoriteEvent missing = scheduled().markUnavailable();
        assertTrue(missing.isUnavailable());
        assertFalse(missing.canRemind());
        assertEquals("2026-11-28", missing.rawDate());
        assertSame(missing, missing.markUnavailable());

        FavoriteEvent restored = missing.updated("活动", "上海市", "2026-11-28", EventStatus.CONFIRMED);
        assertFalse(restored.isUnavailable());
        assertTrue(restored.canRemind());
    }

    @Test
    void datePendingNeverSchedulesAnAlarm() {
        FavoriteEvent original = scheduled();
        FavoriteEvent pending = original.updated("活动", "上海市", "", EventStatus.PLAN);
        FavoriteEvent confirmed = pending.updated("活动", "上海市", "2026-12-05", EventStatus.CONFIRMED);

        assertFalse(pending.canRemind());
        assertFalse(pending.isUpcoming());
        assertEquals(original.rawDate(), pending.previousDate());
        assertEquals("", confirmed.previousDate());
        assertTrue(confirmed.canRemind());
    }

    private static FavoriteEvent scheduled() {
        return new FavoriteEvent(2026, 42, "活动", "上海市", "2026-11-28",
                EventStatus.CONFIRMED, false, null);
    }
}
