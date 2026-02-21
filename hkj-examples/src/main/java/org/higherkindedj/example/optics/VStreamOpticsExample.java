// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.each.VStreamTraversals;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating VStream integration with the optics ecosystem.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li><b>VStreamTraversals</b> - Direct traversal of VStream elements
 *   <li><b>EachInstances.vstreamEach()</b> - Each instance for VStream
 *   <li><b>FocusDSL integration</b> - Using .each(vstreamEach) to navigate into VStream fields
 *   <li><b>TraversalPath.toVStreamPath()</b> - Bridging from optics to VStreamPath
 *   <li><b>VStreamPath.fromEach()</b> - Creating VStreamPath from any Each-traversable source
 * </ul>
 */
public class VStreamOpticsExample {

  // Domain models
  record Rule(String name, int priority) {}

  record Config(String env, VStream<Rule> rules) {}

  // Lenses
  static final Lens<Config, VStream<Rule>> RULES_LENS =
      Lens.of(Config::rules, (config, rules) -> new Config(config.env(), rules));

  static final Lens<Rule, String> RULE_NAME_LENS =
      Lens.of(Rule::name, (rule, name) -> new Rule(name, rule.priority()));

  static final Lens<Rule, Integer> RULE_PRIORITY_LENS =
      Lens.of(Rule::priority, (rule, priority) -> new Rule(rule.name(), priority));

  public static void main(String[] args) {
    System.out.println("=== VStream Optics Integration Examples ===\n");

    demonstrateVStreamTraversal();
    demonstrateVStreamEach();
    demonstrateFocusDSLIntegration();
    demonstrateToVStreamPath();
    demonstrateFromEach();
  }

  /** Demonstrates direct use of VStreamTraversals.forVStream(). */
  private static void demonstrateVStreamTraversal() {
    System.out.println("--- 1. VStream Traversal ---");

    Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
    VStream<String> stream = VStream.fromList(List.of("hello", "world", "foo"));

    // Get all elements
    List<String> all = Traversals.getAll(traversal, stream);
    System.out.println("All elements: " + all);

    // Modify all elements
    VStream<String> upper = Traversals.modify(traversal, String::toUpperCase, stream);
    System.out.println("Uppercased:   " + upper.toList().run());

    System.out.println();
  }

  /** Demonstrates the Each instance for VStream. */
  private static void demonstrateVStreamEach() {
    System.out.println("--- 2. VStream Each Instance ---");

    Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
    VStream<Integer> numbers = VStream.fromList(List.of(1, 2, 3, 4, 5));

    // Traverse via Each
    List<Integer> all = Traversals.getAll(vstreamEach.each(), numbers);
    System.out.println("Elements:         " + all);

    // Modify via Each
    VStream<Integer> doubled = Traversals.modify(vstreamEach.each(), x -> x * 2, numbers);
    System.out.println("Doubled:          " + doubled.toList().run());

    // Check indexed support
    System.out.println("Supports indexed: " + vstreamEach.supportsIndexed());

    System.out.println();
  }

  /** Demonstrates FocusDSL integration with VStream fields. */
  private static void demonstrateFocusDSLIntegration() {
    System.out.println("--- 3. FocusDSL Integration ---");

    Each<VStream<Rule>, Rule> vstreamEach = EachInstances.vstreamEach();

    // Build traversal path: Config -> rules -> each rule
    TraversalPath<Config, Rule> allRules = FocusPath.of(RULES_LENS).each(vstreamEach);

    // Build traversal path: Config -> rules -> each rule -> name
    TraversalPath<Config, String> allRuleNames = allRules.via(RULE_NAME_LENS);

    Config config =
        new Config(
            "production",
            VStream.fromList(
                List.of(
                    new Rule("auth-check", 1),
                    new Rule("rate-limit", 2),
                    new Rule("cors-allow", 3))));

    // Get all rule names
    List<String> names = allRuleNames.getAll(config);
    System.out.println("Rule names: " + names);

    // Modify all rule names to uppercase
    Config updated = allRuleNames.modifyAll(String::toUpperCase, config);
    System.out.println("Updated:    " + allRuleNames.getAll(updated));

    // Filter high-priority rules
    TraversalPath<Config, Rule> highPriority = allRules.filter(r -> r.priority() <= 2);
    List<Rule> important = highPriority.getAll(config);
    System.out.println("High priority rules: " + important.stream().map(Rule::name).toList());

    System.out.println();
  }

  /** Demonstrates TraversalPath.toVStreamPath() bridge. */
  private static void demonstrateToVStreamPath() {
    System.out.println("--- 4. TraversalPath to VStreamPath Bridge ---");

    Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();
    TraversalPath<List<Integer>, Integer> path = TraversalPath.of(listTraversal);

    List<Integer> source = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // Bridge to VStreamPath
    VStreamPath<Integer> vstreamPath = path.toVStreamPath(source);

    // Use lazy VStream operations
    List<Integer> result =
        vstreamPath.filter(n -> n % 2 == 0).map(n -> n * 10).take(3).toList().unsafeRun();

    System.out.println("Source:   " + source);
    System.out.println("Filtered (even), mapped (*10), take(3): " + result);

    System.out.println();
  }

  /** Demonstrates VStreamPath.fromEach() factory. */
  private static void demonstrateFromEach() {
    System.out.println("--- 5. VStreamPath.fromEach() Factory ---");

    // From List
    Each<List<String>, String> listEach = EachInstances.listEach();
    List<String> names = List.of("Alice", "Bob", "Charlie", "Diana");

    VStreamPath<String> fromList = VStreamPath.fromEach(names, listEach);
    List<String> greeting = fromList.map(n -> "Hello, " + n + "!").toList().unsafeRun();
    System.out.println("From List: " + greeting);

    // From VStream
    Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
    VStream<Integer> numbers = VStream.fromList(List.of(10, 20, 30));

    VStreamPath<Integer> fromVStream = VStreamPath.fromEach(numbers, vstreamEach);
    List<Integer> incremented = fromVStream.map(n -> n + 1).toList().unsafeRun();
    System.out.println("From VStream: " + incremented);

    // From Optional
    Each<Optional<String>, String> optEach = EachInstances.optionalEach();
    Optional<String> present = Optional.of("world");

    VStreamPath<String> fromOpt = VStreamPath.fromEach(present, optEach);
    System.out.println("From Optional: " + fromOpt.toList().unsafeRun());

    System.out.println();
  }
}
