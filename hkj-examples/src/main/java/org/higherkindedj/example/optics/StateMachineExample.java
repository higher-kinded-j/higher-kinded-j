// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Dispatched;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Pending;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Returned;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating type-safe state machines using prisms for state transitions.
 *
 * <p>A consignment of an order moves through the chapter's {@link ConsignmentState} as the
 * courier's events arrive: picked up, it is dispatched; returned, it waits to be rebooked.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe state representation with sealed interfaces
 *   <li>State validation using {@code matches()} before transitions
 *   <li>Conditional state transitions guarded with {@code getOptional()} and {@code filter()}
 *   <li>State-specific data access using {@code getOptional()} and {@code mapOptional()}
 *   <li>Complex state transition logic with composed prisms
 * </ul>
 */
public class StateMachineExample {

  // The courier's events, as Advanced Prism Patterns declares them beside the chapter's
  // ConsignmentState
  @GeneratePrisms
  public sealed interface ConsignmentEvent permits PickedUp, ReturnReceived, Rebooked {}

  public record PickedUp(Instant at) implements ConsignmentEvent {}

  public record ReturnReceived(String reason) implements ConsignmentEvent {}

  public record Rebooked() implements ConsignmentEvent {}

  // Prisms for state access
  private static final Prism<ConsignmentState, Pending> PENDING = ConsignmentStatePrisms.pending();
  private static final Prism<ConsignmentState, Dispatched> DISPATCHED =
      ConsignmentStatePrisms.dispatched();
  private static final Prism<ConsignmentState, Returned> RETURNED =
      ConsignmentStatePrisms.returned();

  // Prisms for event access
  private static final Prism<ConsignmentEvent, PickedUp> PICKED_UP =
      ConsignmentEventPrisms.pickedUp();
  private static final Prism<ConsignmentEvent, ReturnReceived> RETURN_RECEIVED =
      ConsignmentEventPrisms.returnReceived();
  private static final Prism<ConsignmentEvent, Rebooked> REBOOKED =
      ConsignmentEventPrisms.rebooked();

  // A consignment's state, and the dispatch it may be in: a lens composed with a prism
  private static final Lens<Consignment, ConsignmentState> STATE = ConsignmentLenses.state();
  private static final Affine<Consignment, Dispatched> DISPATCH = STATE.andThen(DISPATCHED);

  private static final Address HOME = new Address("1 Long Street", "London", "n1 1aa");
  private static final Instant MORNING = Instant.parse("2026-10-02T08:30:00Z");

  public static void main(String[] args) {
    System.out.println("=== State Machine with Prisms ===\n");

    demonstrateBasicTransitions();
    demonstrateConditionalTransitions();
    demonstrateInvalidTransitions();
    demonstrateStateQueries();
    demonstrateComplexWorkflow();
  }

  private static void demonstrateBasicTransitions() {
    System.out.println("--- Basic State Transitions ---");

    // A consignment waiting for a courier
    Consignment consignment = consignment(1, HOME, new Pending());
    System.out.println("Initial state: " + stateName(consignment));

    // Transition: Pending -> Dispatched
    consignment = step(consignment, new PickedUp(MORNING));
    System.out.println("After pickup: " + stateName(consignment));

    // Transition: Dispatched -> Returned
    consignment = step(consignment, new ReturnReceived("No one home"));
    System.out.println("After return: " + stateName(consignment));

    // Transition: Returned -> Pending
    consignment = step(consignment, new Rebooked());
    System.out.println("After rebooking: " + stateName(consignment));

    System.out.println();
  }

  private static void demonstrateConditionalTransitions() {
    System.out.println("--- Conditional State Transitions ---");

    Consignment dispatched = consignment(2, HOME, new Dispatched(MORNING));
    System.out.println("Dispatched consignment: " + dispatched);

    // A return must give a reason
    Consignment result = step(dispatched, new ReturnReceived(""));
    System.out.println("After a return with no reason: " + stateName(result));
    System.out.println("  (Remains Dispatched because a return needs a reason)");

    // Give a reason and try again
    Consignment returned = step(dispatched, new ReturnReceived("Parcel damaged"));
    System.out.println("After a return with a reason: " + stateName(returned));

    // Return a consignment the courier has held for more than a week
    Instant now = MORNING.plus(Duration.ofDays(10));
    Consignment overdue = consignment(3, HOME, new Dispatched(MORNING));
    System.out.println("\nConsignment dispatched ten days ago: " + overdue);

    Consignment maybeReturned =
        STATE.modify(
            state ->
                DISPATCHED
                    .getOptional(state)
                    .filter(dispatch -> dispatch.at().isBefore(now.minus(Duration.ofDays(7))))
                    .map(dispatch -> (ConsignmentState) new Returned("Not delivered within a week"))
                    .orElse(state),
            overdue);

    if (RETURNED.matches(maybeReturned.state())) {
      System.out.println("Consignment was returned as overdue");
      System.out.println(
          "  Reason: "
              + RETURNED.mapOptional(Returned::reason, maybeReturned.state()).orElse("Unknown"));
    }

    System.out.println();
  }

  private static void demonstrateInvalidTransitions() {
    System.out.println("--- Invalid Transition Handling ---");

    // Try to return a consignment no courier has picked up (invalid)
    Consignment pending = consignment(4, HOME, new Pending());
    System.out.println("Attempting to return a pending consignment...");

    // This will fail because the consignment is not Dispatched
    Consignment result = step(pending, new ReturnReceived("Refused"));

    if (PENDING.matches(result.state())) {
      System.out.println("  Transition rejected: consignment still Pending");
    }

    // Try to rebook a consignment a courier has (invalid)
    Consignment dispatched = consignment(5, HOME, new Dispatched(MORNING));
    System.out.println("\nAttempting to rebook a dispatched consignment...");

    Consignment result2 = step(dispatched, new Rebooked());

    if (DISPATCHED.matches(result2.state())) {
      System.out.println("  Transition rejected: consignment still Dispatched");
    }

    System.out.println();
  }

  private static void demonstrateStateQueries() {
    System.out.println("--- State Queries ---");

    List<Consignment> consignments =
        List.of(
            consignment(1, HOME, new Pending()),
            consignment(2, HOME, new Dispatched(MORNING)),
            consignment(3, HOME, new Dispatched(MORNING.plus(Duration.ofHours(2)))),
            consignment(4, HOME, new Returned("Address not found")),
            consignment(5, HOME, new Returned("Refused")));

    // Check which state each consignment is in
    List<ConsignmentState> states = consignments.stream().map(Consignment::state).toList();
    System.out.println("Consignments by state:");
    System.out.println("  Pending:    " + states.stream().filter(PENDING::matches).count());
    System.out.println("  Dispatched: " + states.stream().filter(DISPATCHED::matches).count());
    System.out.println("  Returned:   " + states.stream().filter(RETURNED::matches).count());

    // Read every dispatch time through the lens composed with the prism
    List<Instant> dispatchTimes =
        consignments.stream()
            .flatMap(consignment -> DISPATCH.getOptional(consignment).map(Dispatched::at).stream())
            .toList();

    System.out.println("\nDispatch times: " + dispatchTimes);

    // Check which consignments await a courier (Pending or Returned)
    List<UUID> awaitingOrderIds =
        consignments.stream()
            .filter(consignment -> canRedirect(consignment.state()))
            .map(Consignment::orderId)
            .toList();

    System.out.println("Consignments awaiting a courier: " + awaitingOrderIds);

    System.out.println();
  }

  private static void demonstrateComplexWorkflow() {
    System.out.println("--- Complex Workflow with Branching ---");

    // The address carries a typo the courier will not find
    Address misspelt = new Address("1 Lomg Street", "London", "n1 1aa");
    Consignment consignment = consignment(100, misspelt, new Pending());

    System.out.println("Starting consignment: " + stateName(consignment));

    // The courier picks it up
    consignment = step(consignment, new PickedUp(MORNING));
    System.out.println("1. Picked up by the courier");

    // The courier brings it back
    consignment = step(consignment, new ReturnReceived("Address not found"));
    String reason = RETURNED.mapOptional(Returned::reason, consignment.state()).orElse("Unknown");
    System.out.println("2. Returned: " + reason);

    // Correct the address before rebooking, if that is why it came back
    if (reason.startsWith("Address")) {
      consignment = redirect(consignment, HOME);
      System.out.println("3. Redirected to " + consignment.to().street());
    } else {
      System.out.println("3. Kept the address");
    }

    // Rebook it, and the courier picks it up again
    consignment =
        step(step(consignment, new Rebooked()), new PickedUp(MORNING.plus(Duration.ofDays(1))));
    System.out.println("4. Rebooked and picked up again");

    // A courier has it now, so a redirect is refused
    Consignment refused =
        redirect(consignment, new Address("9 Elsewhere Road", "Leeds", "ls1 1aa"));
    System.out.println("5. Redirect refused: still going to " + refused.to().street());

    System.out.println("\nFinal state: " + stateName(consignment));
    System.out.println("Delivering to: " + consignment.to().street());

    System.out.println();
  }

  // State transition methods

  // Moves a consignment on by one event, or leaves it where it is when the event does not apply
  private static Consignment step(Consignment consignment, ConsignmentEvent event) {
    return transition(consignment.state(), event)
        .map(next -> STATE.set(next, consignment))
        .orElse(consignment);
  }

  // Matches the state and the event together, as Advanced Prism Patterns' machine does
  private static Optional<ConsignmentState> transition(
      ConsignmentState state, ConsignmentEvent event) {
    // Pending -> Dispatched (on pickup)
    if (PENDING.matches(state) && PICKED_UP.matches(event)) {
      return PICKED_UP.mapOptional(pickedUp -> new Dispatched(pickedUp.at()), event);
    }

    // Dispatched -> Returned (on a return that gives a reason)
    if (DISPATCHED.matches(state) && RETURN_RECEIVED.matches(event)) {
      return RETURN_RECEIVED
          .getOptional(event)
          .filter(received -> !received.reason().isBlank())
          .map(received -> new Returned(received.reason()));
    }

    // Returned -> Pending (on rebooking)
    if (RETURNED.matches(state) && REBOOKED.matches(event)) {
      return Optional.of(new Pending());
    }

    return Optional.empty(); // Invalid transition
  }

  // A consignment can be redirected only while no courier has it: Pending or Returned
  private static boolean canRedirect(ConsignmentState state) {
    return PENDING.matches(state) || RETURNED.matches(state);
  }

  private static Consignment redirect(Consignment consignment, Address to) {
    return canRedirect(consignment.state())
        ? ConsignmentLenses.to().set(to, consignment)
        : consignment;
  }

  private static Consignment consignment(long orderNumber, Address to, ConsignmentState state) {
    return new Consignment(new UUID(0, orderNumber), to, state);
  }

  private static String stateName(Consignment consignment) {
    return consignment.state().getClass().getSimpleName();
  }
}
