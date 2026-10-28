package cc.thonly.app.ui;

import cc.thonly.app.data.Event;

/** 列表项上可触发的两种操作，由宿主 Activity 提供具体实现。 */
public interface EventInteraction {

    void onEventSelected(Event event);

    void onAddToCalendar(Event event);
}
