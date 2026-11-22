package cc.thonly.app.notify;

import cc.thonly.app.data.EventStatus;
import cc.thonly.app.data.FavoriteEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ReminderSchedulerTest {

    @Test
    void sameIdInDifferentYearsAndReminderSlotsUsesSeparateAlarmKeys() {
        FavoriteEvent first = new FavoriteEvent(2026, 42, "活动", "", "2026-12-05",
                EventStatus.CONFIRMED, false, null);
        FavoriteEvent second = new FavoriteEvent(2027, 42, "活动", "", "2027-12-05",
                EventStatus.CONFIRMED, false, null);

        String evening = ReminderScheduler.key(first, ReminderScheduler.SLOT_EVENING);
        assertNotEquals(evening, ReminderScheduler.key(second, ReminderScheduler.SLOT_EVENING));
        assertNotEquals(evening, ReminderScheduler.key(first, ReminderScheduler.SLOT_MORNING));
    }
}
