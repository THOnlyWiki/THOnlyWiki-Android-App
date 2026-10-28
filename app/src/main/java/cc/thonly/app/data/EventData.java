package cc.thonly.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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
     * @param region   传 null 或空串表示不限地区
     */
    public List<Event> filter(EventCategory category, String keyword, String region) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Event> result = new ArrayList<Event>();
        for (Event event : events) {
            if (category != null && event.category() != category) {
                continue;
            }
            if (!matchesRegion(event, region)) {
                continue;
            }
            if (!query.isEmpty() && !matches(event, query)) {
                continue;
            }
            result.add(event);
        }
        return result;
    }

    /** 该年出现过的地区，按名称排序，供日历页的地区筛选使用 */
    public List<String> regions() {
        Set<String> regions = new TreeSet<String>();
        for (Event event : events) {
            if (!event.region().isEmpty()) {
                regions.add(event.region());
            }
        }
        return new ArrayList<String>(regions);
    }

    /**
     * @param region null 或空串表示不限地区
     */
    public List<Event> eventsOn(int month, int day, String region) {
        List<Event> result = new ArrayList<Event>();
        for (Event event : events) {
            if (event.date().isResolved()
                    && event.date().month() == month
                    && event.date().day() == day
                    && matchesRegion(event, region)) {
                result.add(event);
            }
        }
        return result;
    }

    /**
     * 该月有活动的日期集合，用于月历上的圆点。
     *
     * @param region null 或空串表示不限地区
     */
    public Set<Integer> markedDaysOf(int month, String region) {
        Set<Integer> days = new HashSet<Integer>();
        for (Event event : events) {
            if (event.date().isResolved()
                    && event.date().month() == month
                    && matchesRegion(event, region)) {
                days.add(event.date().day());
            }
        }
        return days;
    }

    private static boolean matchesRegion(Event event, String region) {
        return region == null || region.isEmpty() || region.equals(event.region());
    }

    /**
     * 关键字匹配范围：名称、地区、状态、类型，以及日期的多种写法。
     *
     * <p>日期不能只比原始文本——服务端给的是 {@code 2026/2/13}，
     * 用户会输「2月13」「2/13」「13日」，这些都得能搜到。
     */
    private static boolean matches(Event event, String query) {
        if (contains(event.name(), query)
                || contains(event.region(), query)
                || contains(event.status().label(), query)
                || contains(event.category().key(), query)
                || contains(event.date().raw(), query)) {
            return true;
        }
        return matchesDate(event.date(), query);
    }

    private static boolean matchesDate(EventDate date, String query) {
        if (!date.isResolved()) {
            return false;
        }
        int month = date.month();
        int day = date.day();
        String[] forms = {
                month + "月" + day + "日",
                month + "月",
                month + "/" + day,
                month + "-" + day,
                month + "." + day,
                day + "日"
        };
        for (String form : forms) {
            if (form.contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
