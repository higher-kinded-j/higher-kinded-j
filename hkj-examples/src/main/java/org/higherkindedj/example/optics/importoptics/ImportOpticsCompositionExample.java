// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics;

import java.time.LocalDate;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A runnable example demonstrating how to compose imported optics with locally generated optics.
 *
 * <p>This example shows how optics generated via {@code @ImportOptics} can be seamlessly composed
 * with optics generated via {@code @GenerateLenses} on your own types.
 */
public class ImportOpticsCompositionExample {

  // Local domain records with generated lenses
  @GenerateLenses
  public record Event(String name, LocalDate date, String location) {}

  @GenerateLenses
  public record Calendar(String owner, Event nextEvent) {}

  public static void main(String[] args) {
    System.out.println("=== ImportOptics Composition Example ===\n");

    // 1. Create sample data
    Event conference = new Event("Java Conference", LocalDate.of(2024, 9, 15), "London");
    Calendar myCalendar = new Calendar("Alice", conference);

    System.out.println("Original calendar: " + myCalendar);
    System.out.println();

    // 2. Compose local lenses with imported lenses
    // Create a lens from Calendar -> Event -> LocalDate -> year
    Lens<Calendar, Event> calendarToEvent = CalendarLenses.nextEvent();
    Lens<Event, LocalDate> eventToDate = EventLenses.date();
    Lens<LocalDate, Integer> dateToYear = LocalDateLenses.year();

    // Compose into a deep lens
    Lens<Calendar, Integer> calendarToYear =
        calendarToEvent.andThen(eventToDate).andThen(dateToYear);

    // 3. Use the composed lens to read
    int eventYear = calendarToYear.get(myCalendar);
    System.out.println("Event year: " + eventYear);

    // 4. Use the composed lens to update deeply
    Calendar updatedCalendar = calendarToYear.set(2025, myCalendar);
    System.out.println("Updated calendar (year 2025): " + updatedCalendar);
    System.out.println("Original unchanged: " + myCalendar);
    System.out.println();

    // 5. Modify using a function
    Calendar postponedCalendar = calendarToYear.modify(y -> y + 1, myCalendar);
    System.out.println("Postponed by 1 year: " + postponedCalendar);
    System.out.println();

    // 6. Combine multiple updates
    // Update both the event name and postpone by 2 years
    Lens<Calendar, String> calendarToEventName = calendarToEvent.andThen(EventLenses.name());

    Calendar fullyUpdated =
        calendarToEventName.set("Java Summit", calendarToYear.modify(y -> y + 2, myCalendar));

    System.out.println("Renamed and postponed: " + fullyUpdated);

    // 7. Create a lens to the day of month for more precise control
    Lens<Calendar, Integer> calendarToDay =
        calendarToEvent.andThen(eventToDate).andThen(LocalDateLenses.dayOfMonth());

    Calendar movedToFirstDay = calendarToDay.set(1, myCalendar);
    System.out.println("Moved to 1st of month: " + movedToFirstDay);

    System.out.println("\n=== Example Complete ===");
  }
}
