// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effect;

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

/** Solution for Tutorial 02: Effect Path Advanced — teaching-solution format. */
@DisplayName("Tutorial 02 Solution: Effect Path Advanced")
public class Tutorial02_EffectPathAdvanced_Solution {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: ForPath flattens the nested-lambda shape that {@code via} chains take
   * once they exceed two steps. Each binding is named at the {@code .yield} site, which is also
   * where the result is built.
   *
   * <p>Alternative: nested {@code via} calls. Identical semantics; reads worse for three or more
   * steps because every step indents.
   *
   * <p>Common wrong attempt: trying to refer to earlier bindings positionally inside intermediate
   * {@code .from} lambdas. Each downstream lambda receives a tuple of the previously bound values;
   * access them with {@code t._1()}, {@code t._2()}, etc. The {@code .yield} site is the one place
   * where every binding is in scope by name.
   */
  @Test
  @DisplayName("Exercise 1: ForPath binds steps as named locals")
  void exercise1_forPathBasics() {
    MaybePath<Integer> result =
        ForPath.from(Path.just(10)).from(n -> Path.just(n * 2)).yield((a, b) -> a + b);
    assertThat(result.getOrElse(0)).isEqualTo(30);

    MaybePath<String> withLet =
        ForPath.from(Path.just("hello"))
            .let(String::length)
            .yield((str, len) -> str + " has " + len + " chars");
    assertThat(withLet.getOrElse("")).isEqualTo("hello has 5 chars");

    MaybePath<Integer> shortCircuit =
        ForPath.from(Path.just(5))
            .<Integer>from(a -> Path.nothing())
            .from(t -> Path.just(100))
            .yield((a, b, c) -> a + b + c);
    assertThat(shortCircuit.run().isNothing()).isTrue();
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: ForPath shines when the workflow has typed errors and several dependent
   * steps. Each step's input is the tuple of previously bound values; the {@code .yield} sees them
   * all by name.
   *
   * <p>Alternative: a chain of {@code via} calls with shadow lambdas. Same semantics, worse
   * readability past three steps.
   *
   * <p>Common wrong attempt: forgetting that the lambdas after the first {@code .from} receive a
   * tuple, not the latest value. Reach into the tuple with {@code t._N()}.
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

    EitherPath<String, String> workflow =
        ForPath.from(Path.<String, String>right("25"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);
    assertThat(workflow.run().getRight()).isEqualTo("Valid age: 25");

    EitherPath<String, String> invalid =
        ForPath.from(Path.<String, String>right("abc"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);
    assertThat(invalid.run().isLeft()).isTrue();
    assertThat(invalid.run().getLeft()).isEqualTo("Invalid age format");

    EitherPath<String, String> outOfRange =
        ForPath.from(Path.<String, String>right("200"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);
    assertThat(outOfRange.run().isLeft()).isTrue();
    assertThat(outOfRange.run().getLeft()).isEqualTo("Age out of range");
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: ErrorContext bundles {@code Either} (typed error) with deferred IO
   * (async) into a single fluent value. We never lose error information through async hops, and we
   * never accidentally execute the workflow before we are ready.
   *
   * <p>Alternative: write {@code CompletableFuture<Either<E, A>>} by hand and chain with {@code
   * thenCompose}. Works, but keeps two error models alive (the {@code Either} and the future's
   * {@code .exceptionally}); ErrorContext collapses both.
   *
   * <p>Common wrong attempt: calling {@code unsafeRun()} eagerly in the middle of the chain. That
   * executes the workflow up to that point and forces the rest to wrap-and-rerun. Defer {@code
   * unsafeRun()} to the boundary; see the diagnostic exercise.
   */
  @Test
  @DisplayName("Exercise 3: ErrorContext combines typed errors with deferred IO")
  void exercise3_errorContext() {
    ErrorContext<?, String, Integer> success = ErrorContext.success(42);
    ErrorContext<?, String, Integer> failure = ErrorContext.failure("Something went wrong");

    Either<String, Integer> successResult = success.runIO().unsafeRun();
    assertThat(successResult.getRight()).isEqualTo(42);

    Either<String, Integer> failureResult = failure.runIO().unsafeRun();
    assertThat(failureResult.getLeft()).isEqualTo("Something went wrong");

    ErrorContext<?, String, String> workflow =
        ErrorContext.<String, Integer>success(10)
            .via(n -> n > 5 ? ErrorContext.success("big: " + n) : ErrorContext.failure("too small"))
            .map(String::toUpperCase);

    Either<String, String> workflowResult = workflow.runIO().unsafeRun();
    assertThat(workflowResult.getRight()).isEqualTo("BIG: 10");

    Either<String, Integer> recovered = failure.recover(err -> -1).runIO().unsafeRun();
    assertThat(recovered.getRight()).isEqualTo(-1);
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: ConfigContext makes the configuration dependency explicit in the type.
   * The same workflow can be run against different configs in tests; in production we run once at
   * the boundary with the real config.
   *
   * <p>Alternative: a Spring DI bean. Same outcome at runtime; ConfigContext makes the dependency
   * visible in the type signature instead of hidden in DI metadata.
   *
   * <p>Common wrong attempt: capturing the config in a closure outside the ConfigContext pipeline.
   * Works, but reintroduces the global state we were trying to avoid.
   */
  @Test
  @DisplayName("Exercise 4: ConfigContext threads configuration through a workflow")
  void exercise4_configContext() {
    record AppConfig(String apiUrl, int timeout, boolean debug) {}

    AppConfig config = new AppConfig("https://api.example.com", 30, true);

    ConfigContext<?, AppConfig, AppConfig> getConfig = ConfigContext.ask();
    AppConfig result = getConfig.runWithSync(config);
    assertThat(result.apiUrl()).isEqualTo("https://api.example.com");

    ConfigContext<?, AppConfig, String> getUrl =
        ConfigContext.<AppConfig>ask().map(AppConfig::apiUrl);
    assertThat(getUrl.runWithSync(config)).isEqualTo("https://api.example.com");

    ConfigContext<?, AppConfig, String> workflow =
        ConfigContext.<AppConfig>ask()
            .via(cfg -> ConfigContext.pure(cfg.apiUrl() + "/users"))
            .via(
                endpoint ->
                    ConfigContext.<AppConfig, String>io(
                        cfg -> endpoint + "?timeout=" + cfg.timeout()));

    String url = workflow.runWithSync(config);
    assertThat(url).isEqualTo("https://api.example.com/users?timeout=30");

    ConfigContext<?, AppConfig, Integer> doubled =
        ConfigContext.<AppConfig, Integer>io(cfg -> cfg.timeout())
            .local(cfg -> new AppConfig(cfg.apiUrl(), cfg.timeout() * 2, cfg.debug()));
    Integer doubledTimeout = doubled.runWithSync(config);
    assertThat(doubledTimeout).isEqualTo(60);
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: MutableContext makes "we're keeping a counter" explicit in the type
   * signature. The state is local to the workflow — no {@code ThreadLocal}, no module-level field,
   * no risk of leaking the state into another request.
   *
   * <p>Alternative: capture an {@code int[]} array in a closure. Compact but globally visible and
   * not testable in isolation.
   *
   * <p>Common wrong attempt: using {@code modify} when {@code put} fits better, or vice versa.
   * Reach for {@code put} when the new state is a constant; for {@code modify} when it depends on
   * the current state.
   */
  @Test
  @DisplayName("Exercise 5: MutableContext threads state through a workflow")
  void exercise5_mutableContext() {
    record Counter(int count) {}

    Counter initial = new Counter(0);

    Counter current = MutableContext.<Counter>get().evalWith(initial).unsafeRun();
    assertThat(current.count()).isEqualTo(0);

    MutableContext<?, Counter, Unit> addTen =
        MutableContext.modify(c -> new Counter(c.count() + 10));

    Counter modified = addTen.execWith(initial).unsafeRun();
    assertThat(modified.count()).isEqualTo(10);

    MutableContext<?, Counter, String> workflow =
        MutableContext.<Counter>modify(c -> new Counter(c.count() + 1))
            .then(() -> MutableContext.modify(c -> new Counter(c.count() + 2)))
            .then(() -> MutableContext.<Counter>get().map(c -> "Final: " + c.count()));

    StateTuple<Counter, String> resultTuple = workflow.runWith(initial).unsafeRun();
    assertThat(resultTuple.value()).isEqualTo("Final: 3");
    assertThat(resultTuple.state().count()).isEqualTo(3);

    Counter replaced = MutableContext.<Counter>put(new Counter(100)).execWith(initial).unsafeRun();
    assertThat(replaced.count()).isEqualTo(100);
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: this is exactly the wrapper code the {@code @GeneratePathBridge}
   * annotation processor emits — one one-liner per service method. We rarely write it by hand; the
   * value is in <em>seeing</em> what the processor does so we can debug if it does not run.
   *
   * <p>Alternative: hand-roll a wrapper class. Same code, more boilerplate per service.
   *
   * <p>Common wrong attempt: forgetting the wrapper exists and calling the original Optional /
   * Either directly from a Path-using site. Works, but loses the fluent shape and forces ad-hoc
   * conversions at every call site.
   */
  @Test
  @DisplayName("Exercise 6: @GeneratePathBridge — wrap a service method by hand")
  void exercise6_generatePathBridgePattern() {
    record User(String id, String name) {}

    Function<String, Optional<User>> findById =
        id -> id.equals("u1") ? Optional.of(new User("u1", "Alice")) : Optional.empty();

    Function<String, OptionalPath<User>> findByIdPath = id -> Path.optional(findById.apply(id));

    OptionalPath<String> userName =
        findByIdPath.apply("u1").map(User::name).map(String::toUpperCase);
    assertThat(userName.run()).contains("ALICE");

    OptionalPath<String> notFound = findByIdPath.apply("u999").map(User::name);
    assertThat(notFound.run()).isEmpty();

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

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code .focus(focusPath)} narrows the EitherPath into the focused field.
   * Two focus calls are equivalent to a single composed FocusPath, but separating them matches how
   * lenses are typically reused across services.
   *
   * <p>Alternative: pre-compose the lenses with {@code addressLens.andThen(cityLens)} and call
   * {@code .focus(...)} once. Use whichever reads better in context.
   *
   * <p>Common wrong attempt: calling {@code .map(u -> cityLens.get(u.address()))} from the Path.
   * Works, but ties the call site to the concrete shape of {@code User} and {@code Address}; the
   * focus form survives a refactor.
   */
  @Test
  @DisplayName("Exercise 7: focus narrows an Effect Path into a nested field")
  void exercise7_focusEffectIntegration() {
    record Address(String city, String country) {}
    record User(String name, Address address) {}

    Lens<User, Address> addressLens = Lens.of(User::address, (u, a) -> new User(u.name(), a));
    Lens<Address, String> cityLens = Lens.of(Address::city, (a, c) -> new Address(c, a.country()));

    FocusPath<User, Address> addressPath = FocusPath.of(addressLens);
    FocusPath<Address, String> cityPath = FocusPath.of(cityLens);

    User alice = new User("Alice", new Address("London", "UK"));

    EitherPath<String, User> userPath = Path.right(alice);

    EitherPath<String, String> cityEffectPath = userPath.focus(addressPath).focus(cityPath);
    assertThat(cityEffectPath.run().getRight()).isEqualTo("London");

    EitherPath<String, User> errorPath = Path.left("User not found");
    EitherPath<String, String> cityFromError = errorPath.focus(addressPath).focus(cityPath);
    assertThat(cityFromError.run().isLeft()).isTrue();
    assertThat(cityFromError.run().getLeft()).isEqualTo("User not found");

    MaybePath<String> complexResult =
        ForPath.from(Path.just(alice))
            .focus(addressPath)
            .focus(t -> t._2().city())
            .yield((u, address, city) -> u.name() + " lives in " + city);
    assertThat(complexResult.getOrElse("")).isEqualTo("Alice lives in London");
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: build the entire workflow as a value, then call {@code
   * .runIO().unsafeRun()} exactly once at the boundary. Everything before that is pure description;
   * all the IO happens at one identifiable point.
   *
   * <p>Alternative: when testing, run the workflow against multiple inputs in a loop. Same
   * single-{@code unsafeRun()}-per-input rule still applies inside the loop.
   *
   * <p>Common wrong attempt: calling {@code .runIO().unsafeRun()} inside a {@code .via} or {@code
   * .map} body to "extract a value early". The result of {@code unsafeRun()} is a plain value, not
   * an ErrorContext, so the rest of the chain has to re-lift it — and the boundary discipline is
   * lost.
   */
  @Test
  @DisplayName("Diagnostic: build the whole workflow first, then run() once at the boundary")
  void diagnostic_runOnlyAtTheBoundary() {
    Either<String, Integer> result =
        ErrorContext.<String, Integer>success(10)
            .map(n -> n * 2)
            .via(n -> n > 0 ? ErrorContext.success(n) : ErrorContext.failure("nope"))
            .runIO()
            .unsafeRun();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(20);
  }
}
