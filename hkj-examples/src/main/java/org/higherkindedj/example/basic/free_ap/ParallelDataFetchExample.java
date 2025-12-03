// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.free_ap;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKind;
import org.higherkindedj.hkt.free_ap.FreeApKindHelper;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;

/**
 * Extended example demonstrating Free Applicative for parallel data fetching.
 *
 * <p>This example simulates a dashboard that needs to fetch data from multiple independent sources:
 *
 * <ul>
 *   <li>User profile
 *   <li>Recent posts
 *   <li>Notifications
 *   <li>Settings
 * </ul>
 *
 * <p>Because these fetches are independent (none needs the result of another), Free Applicative
 * makes this explicit. A smart interpreter could execute them in parallel.
 *
 * <p>The example shows:
 *
 * <ul>
 *   <li>Defining a DSL with sealed interfaces
 *   <li>Building programs with FreeAp
 *   <li>Natural transformation as interpreter
 *   <li>Static analysis: counting operations before execution
 *   <li>Comparison: what would happen with Free Monad
 * </ul>
 *
 * <p>Run with: ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.free_ap.ParallelDataFetchExample
 */
public class ParallelDataFetchExample {

  // ============================================================================
  // Domain Model
  // ============================================================================

  /** User profile data. */
  record User(int id, String name, String email) {}

  /** A post made by a user. */
  record Post(int id, String title, String content) {}

  /** A notification for a user. */
  record Notification(int id, String message, boolean read) {}

  /** User settings. */
  record Settings(String theme, boolean emailNotifications, String language) {}

  /** Complete dashboard data combining all fetched data. */
  record Dashboard(
      User user, List<Post> posts, List<Notification> notifications, Settings settings) {

    @Override
    public String toString() {
      return String.format(
          "Dashboard{\n"
              + "  user=%s\n"
              + "  posts=%d items\n"
              + "  notifications=%d items (%d unread)\n"
              + "  settings=%s\n"
              + "}",
          user,
          posts.size(),
          notifications.size(),
          notifications.stream().filter(n -> !n.read()).count(),
          settings);
    }
  }

  // ============================================================================
  // DSL: Data Fetch Operations
  // ============================================================================

  /** Sealed interface defining all data fetch operations. */
  sealed interface FetchOp<A> {
    record FetchUser(int userId) implements FetchOp<User> {}

    record FetchPosts(int userId) implements FetchOp<List<Post>> {}

    record FetchNotifications(int userId) implements FetchOp<List<Notification>> {}

    record FetchSettings(int userId) implements FetchOp<Settings> {}
  }

  /** HKT bridge for FetchOp. */
  interface FetchOpKind<A> extends Kind<FetchOpKind.Witness, A> {
    final class Witness {
      private Witness() {}
    }
  }

  /** Helper for FetchOp HKT conversions. */
  enum FetchOpHelper {
    FETCH;

    record Holder<A>(FetchOp<A> op) implements FetchOpKind<A> {}

    public <A> Kind<FetchOpKind.Witness, A> widen(FetchOp<A> op) {
      return new Holder<>(op);
    }

    @SuppressWarnings("unchecked")
    public <A> FetchOp<A> narrow(Kind<FetchOpKind.Witness, A> kind) {
      return ((Holder<A>) kind).op();
    }
  }

  // ============================================================================
  // DSL Smart Constructors
  // ============================================================================

  /** Lifts FetchUser into FreeAp. */
  static FreeAp<FetchOpKind.Witness, User> fetchUser(int userId) {
    return FreeAp.lift(FetchOpHelper.FETCH.widen(new FetchOp.FetchUser(userId)));
  }

  /** Lifts FetchPosts into FreeAp. */
  static FreeAp<FetchOpKind.Witness, List<Post>> fetchPosts(int userId) {
    return FreeAp.lift(FetchOpHelper.FETCH.widen(new FetchOp.FetchPosts(userId)));
  }

  /** Lifts FetchNotifications into FreeAp. */
  static FreeAp<FetchOpKind.Witness, List<Notification>> fetchNotifications(int userId) {
    return FreeAp.lift(FetchOpHelper.FETCH.widen(new FetchOp.FetchNotifications(userId)));
  }

  /** Lifts FetchSettings into FreeAp. */
  static FreeAp<FetchOpKind.Witness, Settings> fetchSettings(int userId) {
    return FreeAp.lift(FetchOpHelper.FETCH.widen(new FetchOp.FetchSettings(userId)));
  }

  // ============================================================================
  // Building the Dashboard Program
  // ============================================================================

  /**
   * Builds a FreeAp program that fetches all dashboard data.
   *
   * <p>Key insight: all four fetches are INDEPENDENT. None uses the result of another.
   */
  static FreeAp<FetchOpKind.Witness, Dashboard> buildDashboard(int userId) {
    // Four independent fetches
    FreeAp<FetchOpKind.Witness, User> userFetch = fetchUser(userId);
    FreeAp<FetchOpKind.Witness, List<Post>> postsFetch = fetchPosts(userId);
    FreeAp<FetchOpKind.Witness, List<Notification>> notificationsFetch = fetchNotifications(userId);
    FreeAp<FetchOpKind.Witness, Settings> settingsFetch = fetchSettings(userId);

    // Combine all four using map4 for better readability
    FreeApApplicative<FetchOpKind.Witness> applicative = FreeApApplicative.instance();
    Kind<FreeApKind.Witness<FetchOpKind.Witness>, Dashboard> result =
        applicative.map4(
            FreeApKindHelper.FREE_AP.widen(userFetch),
            FreeApKindHelper.FREE_AP.widen(postsFetch),
            FreeApKindHelper.FREE_AP.widen(notificationsFetch),
            FreeApKindHelper.FREE_AP.widen(settingsFetch),
            Dashboard::new);

    return FreeApKindHelper.FREE_AP.narrow(result);
  }

  // ============================================================================
  // Interpreters
  // ============================================================================

  /** Simulated database with test data. */
  static class SimulatedDatabase {
    private final Map<Integer, User> users = new HashMap<>();
    private final Map<Integer, List<Post>> posts = new HashMap<>();
    private final Map<Integer, List<Notification>> notifications = new HashMap<>();
    private final Map<Integer, Settings> settings = new HashMap<>();
    private final List<String> operationLog = new ArrayList<>();

    SimulatedDatabase() {
      // Seed test data for user 1
      users.put(1, new User(1, "Alice", "alice@example.com"));
      posts.put(
          1,
          List.of(
              new Post(1, "Hello World", "My first post"),
              new Post(2, "FP in Java", "Exploring functional programming")));
      notifications.put(
          1,
          List.of(
              new Notification(1, "Welcome!", true),
              new Notification(2, "New follower", false),
              new Notification(3, "Post liked", false)));
      settings.put(1, new Settings("dark", true, "en"));
    }

    User getUser(int userId) {
      operationLog.add("Fetching user " + userId);
      simulateLatency("user");
      return users.getOrDefault(userId, new User(userId, "Unknown", "unknown@example.com"));
    }

    List<Post> getPosts(int userId) {
      operationLog.add("Fetching posts for user " + userId);
      simulateLatency("posts");
      return posts.getOrDefault(userId, List.of());
    }

    List<Notification> getNotifications(int userId) {
      operationLog.add("Fetching notifications for user " + userId);
      simulateLatency("notifications");
      return notifications.getOrDefault(userId, List.of());
    }

    Settings getSettings(int userId) {
      operationLog.add("Fetching settings for user " + userId);
      simulateLatency("settings");
      return settings.getOrDefault(userId, new Settings("light", false, "en"));
    }

    private void simulateLatency(String operation) {
      // Simulated latency (commented out for fast execution)
      // try { Thread.sleep(100); } catch (InterruptedException e) {}
    }

    List<String> getOperationLog() {
      return new ArrayList<>(operationLog);
    }

    void clearLog() {
      operationLog.clear();
    }
  }

  /**
   * Creates an interpreter that executes fetch operations against the simulated database.
   *
   * <p>This is a natural transformation: FetchOp ~> Id
   */
  static Natural<FetchOpKind.Witness, IdKind.Witness> createInterpreter(SimulatedDatabase db) {
    return new Natural<>() {
      @Override
      @SuppressWarnings("unchecked")
      public <A> Kind<IdKind.Witness, A> apply(Kind<FetchOpKind.Witness, A> fa) {
        FetchOp<A> op = FetchOpHelper.FETCH.narrow(fa);
        A result =
            (A)
                switch (op) {
                  case FetchOp.FetchUser f -> db.getUser(f.userId());
                  case FetchOp.FetchPosts f -> db.getPosts(f.userId());
                  case FetchOp.FetchNotifications f -> db.getNotifications(f.userId());
                  case FetchOp.FetchSettings f -> db.getSettings(f.userId());
                };
        return ID.widen(Id.of(result));
      }
    };
  }

  // ============================================================================
  // Main Example
  // ============================================================================

  public static void main(String[] args) {
    System.out.println("=== Parallel Data Fetch Example ===\n");

    SimulatedDatabase db = new SimulatedDatabase();
    int userId = 1;

    // Build the program
    System.out.println("Building dashboard program for user " + userId + "...\n");
    FreeAp<FetchOpKind.Witness, Dashboard> program = buildDashboard(userId);

    System.out.println("Program structure created. No fetches executed yet!");
    System.out.println("This is the key: we've described WHAT to fetch, not HOW.\n");

    // Show operation log is empty
    System.out.println("Operations executed so far: " + db.getOperationLog().size());
    System.out.println();

    // Create interpreter
    Natural<FetchOpKind.Witness, IdKind.Witness> interpreter = createInterpreter(db);
    IdMonad idApp = IdMonad.instance();

    // Execute the program
    System.out.println("Now interpreting the program...\n");
    Kind<IdKind.Witness, Dashboard> result = program.foldMap(interpreter, idApp);

    // Show results
    Dashboard dashboard = ID.narrow(result).value();
    System.out.println("Dashboard loaded:\n" + dashboard);

    // Show operation log
    System.out.println("\nOperations executed:");
    for (String op : db.getOperationLog()) {
      System.out.println("  - " + op);
    }

    // Key insight
    System.out.println("\n--- Key Insight ---");
    System.out.println("All four fetches are INDEPENDENT in the FreeAp structure.");
    System.out.println("A parallel interpreter could execute them simultaneously.");
    System.out.println("Our simple Id interpreter executes them sequentially,");
    System.out.println("but the program structure allows for optimisation.\n");

    // Comparison with sequential approach
    System.out.println("--- Comparison with Free Monad ---");
    System.out.println("With Free Monad (flatMap), operations would be:");
    System.out.println("  fetchUser(1)");
    System.out.println("    .flatMap(user -> fetchPosts(user.id())");
    System.out.println("      .flatMap(posts -> fetchNotifications(user.id())");
    System.out.println("        .flatMap(notifs -> fetchSettings(user.id())");
    System.out.println("          ...)))");
    System.out.println("\nThis FORCES sequential execution because each step");
    System.out.println("'depends' on the previous (even though we don't use user.id()).\n");

    System.out.println("With Free Applicative (map2), the structure shows independence:");
    System.out.println("  fetchUser(1).map2(fetchPosts(1), ...)");
    System.out.println("              .map2(fetchNotifications(1), ...)");
    System.out.println("              .map2(fetchSettings(1), ...)");
    System.out.println("\nThe interpreter can see all operations and optimise.");
    System.out.println("Example optimisations:");
    System.out.println("  - Execute in parallel (CompletableFuture interpreter)");
    System.out.println("  - Batch into one database query");
    System.out.println("  - Cache and deduplicate requests");

    System.out.println("\n=== Example completed ===");
  }
}
