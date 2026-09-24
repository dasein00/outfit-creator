package com.dasein.poryadok.ui.calendar

import com.dasein.poryadok.data.EventItem
import com.dasein.poryadok.data.Reminder
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.logic.occursOn

fun EventItem.occursOn(day: Long): Boolean = occursOn(this.day, Repeat.of(repeat), day)

fun eventsOn(events: List<EventItem>, day: Long): List<EventItem> =
    events.filter { it.occursOn(day) }.sortedBy { it.startMin ?: -1 }

fun remindersOn(reminders: List<Reminder>, day: Long): List<Reminder> =
    reminders.filter { r ->
        if (r.done) r.day == day else occursOn(r.day, Repeat.of(r.repeat), day)
    }.sortedBy { it.min }
