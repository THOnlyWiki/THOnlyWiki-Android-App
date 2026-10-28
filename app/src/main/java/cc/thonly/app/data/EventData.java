package cc.thonly.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 某一年的活动集合，构造时即完成排序，对外只读。 */
public final class EventData {

    /** 有日期的按时间升序排前面，没日期的按名称排在后面 */
    private static final Comparator<Event> BY_DATE = new Comparator<Event>() {
        @Override
        public int compare(Event left, Event right) {
            boolean leftResolved = left.date().isResolved();
            boolean rightResolved = right.date().isResolved();

            if (leftResolved && rightResolved) {
                if (left.date().month() != right.date().month()) {
                    return left.date().month() - right.date().month();
                }
                if (left.date().day() != right.date().day()) {
                    return left.date().day() - right.date().day();
                }
                return left.name().compareTo(right.name());
            }
            if (leftResolved != rightResolved) {
                return leftResolved ? -1 : 1;
            }
            return left.name().compareTo(right.name());
        }
    };

    private final int year;
    private final List<Integer> years;
    private final List<Event> events;

    public EventData(int year, List<Integer> years, List<Event> events) {
        this.year = year;
        this.years = Collections.unmodifiableList(new ArrayList<Integer>(years));

        List<Event> sorted = new ArrayList<Event>(events);
        Collections.sort(sorted, BY_DATE);
        this.events = Collections.unmodifiableList(sorted);
    }

    public static EventData empty() {
        return new EventData(0, new ArrayList<Integer>(), new ArrayList<Event>());
    }

    public int year() {
        return year;
    }

    public List<Integer> years() {
        return years;
    }

    public List<Event> events() {
        return events;
    }

    /**
     * @param category 传 null 表示不限类型
     * @param keyword  空字符串表示不筛选
     */
    public List<Event> filter(EventCategory category, String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Event> result = new ArrayList<Event>();
        for (Event event : events) {
            if (category != null && event.category() != category) {
                continue;
            }
            if (!query.isEmpty() && !matches(event, query)) {
                continue;
            }
            result.add(event);
        }
        return result;
    }

    public List<Event> eventsOn(int month, int day) {
        List<Event> result = new ArrayList<Event>();
        for (Event event : events) {
            if (event.date().isResolved()
                    && event.date().month() == month
                    && event.date().day() == day) {
                result.add(event);
            }
        }
        return result;
    }

    /** 该月有活动的日期集合，用于月历上的圆点 */
    public Set<Integer> markedDaysOf(int month) {
        Set<Integer> days = new HashSet<Integer>();
        for (Event event : events) {
            if (event.date().isResolved() && event.date().month() == month) {
                days.add(event.date().day());
            }
        }
        return days;
    }

    private static boolean matches(Event event, String query) {
        return event.name().toLowerCase(Locale.ROOT).contains(query)
                || event.region().toLowerCase(Locale.ROOT).contains(query)
                || event.status().label().toLowerCase(Locale.ROOT).contains(query)
                || event.category().key().toLowerCase(Locale.ROOT).contains(query)
                || event.date().raw().toLowerCase(Locale.ROOT).contains(query);
    }
}
