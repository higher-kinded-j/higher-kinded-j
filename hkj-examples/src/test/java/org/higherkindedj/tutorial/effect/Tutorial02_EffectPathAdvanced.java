// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Effect Path Advanced — ForPath, Contexts, and Service Integration.
 *
 * <p>Pain → Promise. Once a workflow has more than three steps, fluent chains start to crowd:
 *
 * <pre>
 *   parsePort(raw)
 *     .via(this::validateRange)
 *     .via(port -&gt;
 *         loadConfig(port)
 *             .map(cfg -&gt; cfg.withTimeout(defaultTimeout()))
 *             .via(this::connect))
 *     .map(connection -&gt; new ServerHandle(port, connection));
 * </pre>
 *
 * <p>Indented lambdas, named-parameter shadowing, hidden tuples — all friction. {@link ForPath} is
 * the for-comprehension that turns the above into a flat sequence of bindings, the same way a
 * {@code for} loop in Scala or a {@code do} block in Haskell does.
 *
 * <p>Java idiom anchor.
 *
 * <ul>
 *   <li>{@link ErrorContext} stands in for "{@code CompletableFuture<Either<Error, A>>}", giving us
 *       typed errors and async wrapped in one fluent shape.
 *   <li>{@link ConfigContext} is the {@code Reader} pattern Spring gives us via DI, expressed as a
 *       value rather than a wiring concern.
 *   <li>{@link MutableContext} is the same idea as a {@code ThreadLocal} accumulator, except the
 *       mutation is local to the workflow and explicit in the type.
 *   <li>{@code @GeneratePathBridge} is to repository interfaces what Spring Data is to JPA: a
 *       generated wrapper that returns {@link Path}s instead of raw Optionals or Eithers.
 * </ul>
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Use {@link ForPath} to bind several steps of a workflow as named locals.
 *   <li>Use {@link ForPath} with {@link EitherPath} for typed-error workflows.
 *   <li>Use {@link ErrorContext} to combine async (deferred IO) with typed errors in one type.
 *   <li>Use {@link ConfigContext} to thread configuration through a workflow without DI.
 *   <li>Use {@link MutableContext} to maintain workflow-local state.
 *   <li>Practise the {@code @GeneratePathBridge} pattern by hand to see what it generates.
 *   <li>Use {@code .focus(...)} to navigate into nested records inside an Effect Path.
 * </ol>
 *
 * <p>Prerequisites: complete Tutorial 01 (Effect Path Basics) first; the operations here build on
 * the four operations from that tutorial.
 *
 * <p>For the chapter-level overview see <a
 * href="../../../../../../../../../hkj-book/src/effect/forpath_comprehension.md">ForPath
 * Comprehension</a> and <a
 * href="../../../../../../../../../hkj-book/src/effect/effect_contexts.md">Effect Contexts</a>.
 */
@DisplayName("Tutorial 02: Effect Path Advanced")
public class Tutorial02_EffectPathAdvanced {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 1: ForPath comprehensions
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: ForPath basics on {@link MaybePath}.
   *
   * <p>{@code ForPath.from(p)} starts a comprehension. Subsequent {@code .from(...)} calls bind
   * each step to a name; {@code .let(...)} binds a pure value (no new effect); {@code .yield(...)}
   * combines the bound values into the final result.
   *
   * <pre>
   *   // Nudge:    Three pieces - start, dependent step, combiner.
   *   // Strategy: ForPath.from(start).from(step).yield(combiner)
   *   // Spoiler:  ForPath.from(Path.just(10))
   *   //               .from(n -&gt; Path.just(n * 2))
   *   //               .yield((a, b) -&gt; a + b)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: ForPath binds steps as named locals")
  void exercise1_forPathBasics() {
    MaybePath<Integer> result = answerRequired();

    assertThat(result.getOrElse(0)).isEqualTo(30); // 10 + 20

    // Sanity: let binds pure values without creating a new path.
    MaybePath<String> withLet =
        ForPath.from(Path.just("hello"))
            .let(String::length)
            .yield((str, len) -> str + " has " + len + " chars");
    assertThat(withLet.getOrElse("")).isEqualTo("hello has 5 chars");

    // Sanity: Nothing short-circuits the rest of the chain.
    MaybePath<Integer> shortCircuit =
        ForPath.from(Path.just(5))
            .<Integer>from(a -> Path.nothing())
            .from(t -> Path.just(100))
            .yield((a, b, c) -> a + b + c);
    assertThat(shortCircuit.run().isNothing()).isTrue();
  }

  /**
   * Exercise 2: ForPath on {@link EitherPath} — typed-error workflows.
   *
   * <p>The bind shape is the same; the failure semantics are the same as for the underlying Path.
   * Each downstream lambda receives a tuple of the previously bound values; we extract via {@code
   * t._1()}, {@code t._2()}, etc.
   *
   * <pre>
   *   // Nudge:    Same shape as exercise 1; pull the parsed value out of the tuple.
   *   // Strategy: ForPath.from(input).from(parseAge).from(t -&gt; validateRange.apply(t._2()))
   *   //               .yield((input, parsed, validated) -&gt; "Valid age: " + validated)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: ForPath on EitherPath shows typed error short-circuiting")
  void exercise2_forPathWithEither() {
    Function<String, EitherPath<String, Integer>> parseAge =
        s -> {
          try {
            return Path.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Path.left("Invalid age format");
          }
        };

    Function<Integer, EitherPath<String, Integer>> validateRange =
        age -> (age >= 0 && age <= 150) ? Path.right(age) : Path.left("Age out of range");

    EitherPath<String, String> workflow = answerRequired();

    assertThat(workflow.run().getRight()).isEqualTo("Valid age: 25");

    // Sanity: invalid format short-circuits at parse.
    EitherPath<String, String> invalid =
        ForPath.from(Path.<String, String>right("abc"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);
    assertThat(invalid.run().isLeft()).isTrue();
    assertThat(invalid.run().getLeft()).isEqualTo("Invalid age format");

    // Sanity: out-of-range short-circuits at validation.
    EitherPath<String, String> outOfRange =
        ForPath.from(Path.<String, String>right("200"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);
    assertThat(outOfRange.run().isLeft()).isTrue();
    assertThat(outOfRange.run().getLeft()).isEqualTo("Age out of range");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 2: Effect Contexts
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: {@link ErrorContext} — typed errors plus deferred IO.
   *
   * <p>{@link ErrorContext} wraps an {@code EitherT} monad transformer. It is what we reach for
   * when we need <em>both</em> typed error handling <em>and</em> deferred execution in a single
   * fluent value. Internally it composes {@link Either} with an IO layer; from our point of view it
   * is one type with the usual Path operations.
   *
   * <pre>
   *   // Nudge:    Same factory naming as Path - success/failure.
   *   // Strategy: ErrorContext.success(...) / ErrorContext.failure(...)
   *   // Spoiler:  ErrorContext.success(42) / ErrorContext.failure("Something went wrong")
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: ErrorContext combines typed errors with deferred IO")
  void exercise3_errorContext() {
    ErrorContext<?, String, Integer> success = answerRequired();

    ErrorContext<?, String, Integer> failure = answerRequired();

    Either<String, Integer> successResult = success.runIO().unsafeRun();
    assertThat(successResult.getRight()).isEqualTo(42);

    Either<String, Integer> failureResult = failure.runIO().unsafeRun();
    assertThat(failureResult.getLeft()).isEqualTo("Something went wrong");

    // Sanity: the same map / via / recover surface as the Path API.
    ErrorContext<?, String, String> workflow =
        ErrorContext.<String, Integer>success(10)
            .via(n -> n > 5 ? ErrorContext.success("big: " + n) : ErrorContext.failure("too small"))
            .map(String::toUpperCase);

    Either<String, String> workflowResult = workflow.runIO().unsafeRun();
    assertThat(workflowResult.getRight()).isEqualTo("BIG: 10");

    Either<String, Integer> recovered = failure.recover(err -> -1).runIO().unsafeRun();
    assertThat(recovered.getRight()).isEqualTo(-1);
  }

  /**
   * Exercise 4: {@link ConfigContext} — dependency injection as a value.
   *
   * <p>{@link ConfigContext} wraps the {@code Reader} pattern. Instead of passing a configuration
   * value down through every method, we describe the workflow once and run it later with a
   * configuration. {@code ask()} retrieves the whole config; {@code pure(v)} lifts a value; {@code
   * io(f)} is "compute a value from the config".
   *
   * <pre>
   *   // Nudge:    ask() retrieves the config; chain via to build with it.
   *   // Strategy: ConfigContext.&lt;AppConfig&gt;ask()
   *   //               .via(cfg -&gt; ConfigContext.pure(cfg.apiUrl() + "/users"))
   *   //               .via(endpoint -&gt; ConfigContext.&lt;AppConfig, String&gt;io(
   *   //                   cfg -&gt; endpoint + "?timeout=" + cfg.timeout()))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: ConfigContext threads configuration through a workflow")
  void exercise4_configContext() {
    record AppConfig(String apiUrl, int timeout, boolean debug) {}

    AppConfig config = new AppConfig("https://api.example.com", 30, true);

    ConfigContext<?, AppConfig, AppConfig> getConfig = answerRequired();

    AppConfig result = getConfig.runWithSync(config);
    assertThat(result.apiUrl()).isEqualTo("https://api.example.com");

    // Sanity: ask().map() picks one field.
    ConfigContext<?, AppConfig, String> getUrl =
        ConfigContext.<AppConfig>ask().map(AppConfig::apiUrl);
    assertThat(getUrl.runWithSync(config)).isEqualTo("https://api.example.com");

    ConfigContext<?, AppConfig, String> workflow = answerRequired();

    String url = workflow.runWithSync(config);
    assertThat(url).isEqualTo("https://api.example.com/users?timeout=30");

    // Sanity: local() runs a sub-computation with a modified config.
    ConfigContext<?, AppConfig, Integer> doubled =
        ConfigContext.<AppConfig, Integer>io(cfg -> cfg.timeout())
            .local(cfg -> new AppConfig(cfg.apiUrl(), cfg.timeout() * 2, cfg.debug()));

    Integer doubledTimeout = doubled.runWithSync(config);
    assertThat(doubledTimeout).isEqualTo(60);
  }

  /**
   * Exercise 5: {@link MutableContext} — workflow-local state.
   *
   * <p>{@link MutableContext} wraps the {@code State} monad. Use it when a workflow needs to
   * accumulate or transform state without resorting to a {@code ThreadLocal} or a mutable field.
   * The state is local to the workflow and explicit in the type.
   *
   * <pre>
   *   // Nudge:    modify takes a function State -&gt; State.
   *   // Strategy: MutableContext.&lt;Counter&gt;modify(c -&gt; new Counter(c.count() + 10))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: MutableContext threads state through a workflow")
  void exercise5_mutableContext() {
    record Counter(int count) {}

    Counter initial = new Counter(0);

    // Sanity: get retrieves current state.
    Counter current = MutableContext.<Counter>get().evalWith(initial).unsafeRun();
    assertThat(current.count()).isEqualTo(0);

    MutableContext<?, Counter, Unit> addTen = answerRequired();

    Counter modified = addTen.execWith(initial).unsafeRun();
    assertThat(modified.count()).isEqualTo(10);

    // Sanity: chain stateful operations using then().
    MutableContext<?, Counter, String> workflow =
        MutableContext.<Counter>modify(c -> new Counter(c.count() + 1))
            .then(() -> MutableContext.modify(c -> new Counter(c.count() + 2)))
            .then(() -> MutableContext.<Counter>get().map(c -> "Final: " + c.count()));

    StateTuple<Counter, String> resultTuple = workflow.runWith(initial).unsafeRun();
    assertThat(resultTuple.value()).isEqualTo("Final: 3");
    assertThat(resultTuple.state().count()).isEqualTo(3);

    // Sanity: put replaces state entirely.
    Counter replaced = MutableContext.<Counter>put(new Counter(100)).execWith(initial).unsafeRun();
    assertThat(replaced.count()).isEqualTo(100);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 3: Annotations and integration
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: The {@code @GeneratePathBridge} pattern, by hand.
   *
   * <p>In production code we annotate service interfaces:
   *
   * <pre>{@code
   * @GeneratePathBridge
   * public interface UserService {
   *     @PathVia
   *     Optional<User> findById(Long id);
   *
   *     @PathVia
   *     Either<Error, User> createUser(CreateUserRequest request);
   * }
   * }</pre>
   *
   * <p>The annotation processor generates {@code UserServicePaths} with methods returning {@code
   * OptionalPath<User>} and {@code EitherPath<Error, User>}. This exercise rebuilds one of those
   * wrappers by hand so we see exactly what is generated.
   *
   * <pre>
   *   // Nudge:    Wrap the Optional in a Path.optional.
   *   // Strategy: id -&gt; Path.optional(findById.apply(id))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: @GeneratePathBridge — wrap a service method by hand")
  void exercise6_generatePathBridgePattern() {
    record User(String id, String name) {}

    Function<String, Optional<User>> findById =
        id -> id.equals("u1") ? Optional.of(new User("u1", "Alice")) : Optional.empty();

    Function<String, OptionalPath<User>> findByIdPath = answerRequired();

    OptionalPath<String> userName =
        findByIdPath.apply("u1").map(User::name).map(String::toUpperCase);
    assertThat(userName.run()).contains("ALICE");

    OptionalPath<String> notFound = findByIdPath.apply("u999").map(User::name);
    assertThat(notFound.run()).isEmpty();

    // Sanity: same shape for an Either-returning method.
    Function<String, Either<String, User>> createUser =
        name ->
            name.isBlank()
                ? Either.left("Name cannot be blank")
                : Either.right(new User("new-id", name));

    Function<String, EitherPath<String, User>> createUserPath =
        name -> Path.either(createUser.apply(name));

    EitherPath<String, String> createResult =
        createUserPath.apply("Bob").map(User::name).map(name -> "Created: " + name);
    assertThat(createResult.run().getRight()).isEqualTo("Created: Bob");
  }

  /**
   * Exercise 7: {@code .focus(...)} — optic navigation inside an Effect Path.
   *
   * <p>An Effect Path can be {@code .focus(focusPath)}'d to narrow into a nested field. The Path
   * API and the Focus DSL share the {@code via} composition operator, so they compose naturally.
   *
   * <p>(Tutorial 14 of the Optics journey covers the bridge in detail.)
   *
   * <pre>
   *   // Nudge:    Compose the two focus paths through .focus().
   *   // Strategy: userPath.focus(addressPath).focus(cityPath)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: focus narrows an Effect Path into a nested field")
  void exercise7_focusEffectIntegration() {
    record Address(String city, String country) {}

    record User(String name, Address address) {}

    // Manual lens definitions for clarity in this anchor; in production we annotate the records
    // with @GenerateLenses and use the generated UserLenses.address() / AddressLenses.city().
    Lens<User, Address> addressLens = Lens.of(User::address, (u, a) -> new User(u.name(), a));
    Lens<Address, String> cityLens = Lens.of(Address::city, (a, c) -> new Address(c, a.country()));

    FocusPath<User, Address> addressPath = FocusPath.of(addressLens);
    FocusPath<Address, String> cityPath = FocusPath.of(cityLens);

    User alice = new User("Alice", new Address("London", "UK"));

    EitherPath<String, User> userPath = Path.right(alice);

    EitherPath<String, String> cityEffectPath = answerRequired();

    assertThat(cityEffectPath.run().getRight()).isEqualTo("London");

    // Sanity: focus preserves errors.
    EitherPath<String, User> errorPath = Path.left("User not found");
    EitherPath<String, String> cityFromError = errorPath.focus(addressPath).focus(cityPath);
    assertThat(cityFromError.run().isLeft()).isTrue();
    assertThat(cityFromError.run().getLeft()).isEqualTo("User not found");

    // Sanity: ForPath + focus for richer compositions.
    MaybePath<String> complexResult =
        ForPath.from(Path.just(alice))
            .focus(addressPath)
            .focus(t -> t._2().city())
            .yield((u, address, city) -> u.name() + " lives in " + city);
    assertThat(complexResult.getOrElse("")).isEqualTo("Alice lives in London");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: do not call {@code .runIO().unsafeRun()} in the middle of a workflow.
   *
   * <p>{@link ErrorContext} (and any IO-bearing context) is a <em>description</em> of work. Calling
   * {@code unsafeRun()} executes the description and returns the result; doing this in the middle
   * of a workflow forces the rest of the workflow to wrap-and-run again. The boundary of the
   * program is the only place {@code unsafeRun()} should appear.
   *
   * <p>The exercise below shows the right shape: build the whole workflow as a value, then run once
   * at the boundary.
   *
   * <pre>
   *   // Nudge:    Chain map / via on the ErrorContext, then call runIO().unsafeRun() at the end.
   *   // Strategy: ErrorContext.&lt;String, Integer&gt;success(10).map(n -&gt; n * 2)
   *   //               .via(n -&gt; n &gt; 0 ? ErrorContext.success(n) : ErrorContext.failure("nope"))
   *   //               .runIO().unsafeRun()
   *   // Spoiler:  see the solution.
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: build the whole workflow first, then run() once at the boundary")
  void diagnostic_runOnlyAtTheBoundary() {
    Either<String, Integer> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(20);
  }

  /*
   * Where to next?
   *   • Concurrency journeys — VTask and Scope/Resource. The same Path API surface, applied to
   *     virtual-thread-based async work.
   *   • Resilience journey — Circuit Breaker, Saga, Retry, Bulkhead built on top of VTaskPath.
   *   • Optics: Focus DSL — combine optic navigation with Effect Paths in a single fluent API.
   */
}
