// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateOp Test Suite")
class StateOpTest {

  // === Test fixtures ===

  record Person(String name, int age, List<String> tags) {}

  private static final Lens<Person, String> NAME_LENS =
      Lens.of(Person::name, (person, name) -> new Person(name, person.age(), person.tags()));

  private static final Lens<Person, Integer> AGE_LENS =
      Lens.of(Person::age, (person, age) -> new Person(person.name(), age, person.tags()));

  private static final Getter<Person, String> NAME_GETTER = Getter.of(Person::name);

  private static final Lens<Person, List<String>> TAGS_LENS =
      Lens.of(Person::tags, (person, tags) -> new Person(person.name(), person.age(), tags));

  // Prism for age > 0 (positive age)
  private static final Prism<Person, Integer> POSITIVE_AGE_PRISM =
      Prism.of(
          person -> person.age() > 0 ? Optional.of(person.age()) : Optional.empty(),
          age -> new Person("", age, List.of()));

  private static final Traversal<List<String>, String> LIST_TRAVERSAL =
      EachInstances.<String>listEach().each();

  private static final Person INITIAL_PERSON = new Person("Alice", 30, List.of("admin", "user"));

  // ===== StateOp construction =====

  @Nested
  @DisplayName("StateOp construction")
  class Construction {

    @Test
    @DisplayName("View creates a view operation")
    void viewCreatesStateOp() {
      StateOp<Person, String> op = new StateOp.View<>(NAME_GETTER, Function.identity());
      assertThat(op).isInstanceOf(StateOp.View.class);
    }

    @Test
    @DisplayName("Over creates a modify operation")
    void overCreatesStateOp() {
      Function<String, String> upper = String::toUpperCase;
      StateOp<Person, String> op = new StateOp.Over<>(NAME_LENS, upper, Function.identity());
      assertThat(op).isInstanceOf(StateOp.Over.class);
    }

    @Test
    @DisplayName("Assign creates a set operation")
    void assignCreatesStateOp() {
      StateOp<Person, String> op = new StateOp.Assign<>(NAME_LENS, "Bob", Function.identity());
      assertThat(op).isInstanceOf(StateOp.Assign.class);
    }

    @Test
    @DisplayName("Preview creates a preview operation")
    void previewCreatesStateOp() {
      var op = new StateOp.Preview<>(POSITIVE_AGE_PRISM, Function.identity());
      assertThat(op).isInstanceOf(StateOp.Preview.class);
    }

    @Test
    @DisplayName("TraverseOver creates a traverse operation")
    void traverseOverCreatesStateOp() {
      var op = new StateOp.TraverseOver<>(LIST_TRAVERSAL, String::toUpperCase, Function.identity());
      assertThat(op).isInstanceOf(StateOp.TraverseOver.class);
    }

    @Test
    @DisplayName("GetState creates a get-state operation")
    void getStateCreatesStateOp() {
      StateOp<Person, Person> op = new StateOp.GetState<>(Function.identity());
      assertThat(op).isInstanceOf(StateOp.GetState.class);
    }

    @Test
    @DisplayName("View rejects null optic")
    void viewRejectsNull() {
      assertThatThrownBy(() -> new StateOp.View<>(null, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Over rejects null optic")
    void overRejectsNullOptic() {
      assertThatThrownBy(
              () ->
                  new StateOp.Over<Person, String, String>(
                      null, String::toUpperCase, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Over rejects null function")
    void overRejectsNullFunction() {
      assertThatThrownBy(
              () -> new StateOp.Over<Person, String, String>(NAME_LENS, null, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Assign rejects null optic")
    void assignRejectsNullOptic() {
      assertThatThrownBy(() -> new StateOp.Assign<>(null, "Bob", Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Assign rejects null value")
    void assignRejectsNullValue() {
      assertThatThrownBy(() -> new StateOp.Assign<>(NAME_LENS, null, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Preview rejects null optic")
    void previewRejectsNull() {
      assertThatThrownBy(() -> new StateOp.Preview<>(null, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TraverseOver rejects null optic")
    void traverseOverRejectsNullOptic() {
      assertThatThrownBy(
              () ->
                  new StateOp.TraverseOver<List<String>, String, List<String>>(
                      null, String::toUpperCase, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("TraverseOver rejects null function")
    void traverseOverRejectsNullFunction() {
      assertThatThrownBy(
              () -> new StateOp.TraverseOver<>(LIST_TRAVERSAL, null, Function.identity()))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Smart constructors =====

  @Nested
  @DisplayName("StateOps smart constructors")
  class SmartConstructors {

    @Test
    @DisplayName("view() lifts getter into Free")
    void viewLiftsFree() {
      Free<StateOpKind.Witness<Person>, String> program = StateOps.view(NAME_GETTER);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("over() lifts lens modify into Free")
    void overLiftsFree() {
      Free<StateOpKind.Witness<Person>, String> program =
          StateOps.over(NAME_LENS, String::toUpperCase);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("assign() lifts lens set into Free")
    void assignLiftsFree() {
      Free<StateOpKind.Witness<Person>, String> program = StateOps.assign(NAME_LENS, "Bob");
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("preview() lifts prism into Free")
    void previewLiftsFree() {
      Free<StateOpKind.Witness<Person>, Optional<Integer>> program =
          StateOps.preview(POSITIVE_AGE_PRISM);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("getState() lifts into Free")
    void getStateLiftsFree() {
      Free<StateOpKind.Witness<Person>, Person> program = StateOps.getState();
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("view() rejects null optic")
    void viewRejectsNull() {
      assertThatThrownBy(() -> StateOps.<Person, String>view((Getter<Person, String>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("over() rejects null optic")
    void overRejectsNullOptic() {
      assertThatThrownBy(() -> StateOps.<Person, String>over(null, String::toUpperCase))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("assign() rejects null value")
    void assignRejectsNullValue() {
      assertThatThrownBy(() -> StateOps.assign(NAME_LENS, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== KindHelper =====

  @Nested
  @DisplayName("StateOpKindHelper")
  class KindHelperTests {

    @Test
    @DisplayName("widen then narrow round-trips")
    void widenNarrowRoundTrips() {
      StateOp<Person, String> original = new StateOp.View<>(NAME_GETTER, Function.identity());
      Kind<StateOpKind.Witness<Person>, String> kind = StateOpKindHelper.STATE_OP.widen(original);
      StateOp<Person, String> roundTripped = StateOpKindHelper.STATE_OP.narrow(kind);
      assertThat(roundTripped).isSameAs(original);
    }

    @Test
    @DisplayName("widen(null) throws NullPointerException")
    void widenNullThrows() {
      assertThatThrownBy(() -> StateOpKindHelper.STATE_OP.widen(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("narrow(null) throws NullPointerException")
    void narrowNullThrows() {
      assertThatThrownBy(() -> StateOpKindHelper.STATE_OP.<Person, String>narrow(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Functor =====

  @Nested
  @DisplayName("StateOpFunctor")
  class FunctorTests {

    @Test
    @DisplayName("map on View preserves operation (cast-through)")
    void mapPreservesOperation() {
      StateOpFunctor<Person> functor = StateOpFunctor.instance();
      Kind<StateOpKind.Witness<Person>, String> kind =
          StateOpKindHelper.STATE_OP.widen(new StateOp.View<>(NAME_GETTER, Function.identity()));

      Kind<StateOpKind.Witness<Person>, Integer> mapped = functor.map(String::length, kind);

      StateOp<Person, Integer> result = StateOpKindHelper.STATE_OP.narrow(mapped);
      assertThat(result).isInstanceOf(StateOp.View.class);
    }

    @Test
    @DisplayName("map rejects null function")
    void mapRejectsNullFunction() {
      StateOpFunctor<Person> functor = StateOpFunctor.instance();
      Kind<StateOpKind.Witness<Person>, String> kind =
          StateOpKindHelper.STATE_OP.widen(new StateOp.View<>(NAME_GETTER, Function.identity()));

      assertThatThrownBy(() -> functor.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map rejects null Kind argument")
    void mapRejectsNullKind() {
      StateOpFunctor<Person> functor = StateOpFunctor.instance();
      assertThatThrownBy(() -> functor.map(Object::toString, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== StateOpInterpreter: interpret into State monad =====

  @Nested
  @DisplayName("StateOpInterpreter (State monad)")
  class StateInterpreterTests {

    private final StateOpInterpreter<Person> interpreter = new StateOpInterpreter<>();
    private final StateMonad<Person> monad = new StateMonad<>();

    @Test
    @DisplayName("View reads value through Getter")
    void viewReadsValue() {
      Free<StateOpKind.Witness<Person>, String> program = StateOps.view(NAME_GETTER);
      Kind<StateKind.Witness<Person>, String> result = program.foldMap(interpreter, monad);
      StateTuple<Person, String> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo("Alice");
      assertThat(tuple.state()).isEqualTo(INITIAL_PERSON);
    }

    @Test
    @DisplayName("View reads value through Lens (as Getter)")
    void viewReadsViaThroughLens() {
      // Lens extends Getter via its asFold/get method
      Free<StateOpKind.Witness<Person>, String> program = StateOps.view(NAME_LENS);
      Kind<StateKind.Witness<Person>, String> result = program.foldMap(interpreter, monad);
      StateTuple<Person, String> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Over modifies focus and returns new value")
    void overModifiesFocus() {
      Free<StateOpKind.Witness<Person>, String> program =
          StateOps.over(NAME_LENS, String::toUpperCase);
      Kind<StateKind.Witness<Person>, String> result = program.foldMap(interpreter, monad);
      StateTuple<Person, String> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo("ALICE");
      assertThat(tuple.state().name()).isEqualTo("ALICE");
      assertThat(tuple.state().age()).isEqualTo(30); // other fields unchanged
    }

    @Test
    @DisplayName("Assign sets focus to fixed value")
    void assignSetsFocus() {
      Free<StateOpKind.Witness<Person>, String> program = StateOps.assign(NAME_LENS, "Bob");
      Kind<StateKind.Witness<Person>, String> result = program.foldMap(interpreter, monad);
      StateTuple<Person, String> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo("Bob");
      assertThat(tuple.state().name()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Preview returns Optional.of when prism matches")
    void previewReturnsWhenMatches() {
      Free<StateOpKind.Witness<Person>, Optional<Integer>> program =
          StateOps.preview(POSITIVE_AGE_PRISM);
      Kind<StateKind.Witness<Person>, Optional<Integer>> result =
          program.foldMap(interpreter, monad);
      StateTuple<Person, Optional<Integer>> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isPresent().contains(30);
    }

    @Test
    @DisplayName("Preview returns Optional.empty when prism does not match")
    void previewReturnsEmptyWhenNoMatch() {
      Person zeroPerson = new Person("Zero", 0, List.of());
      Free<StateOpKind.Witness<Person>, Optional<Integer>> program =
          StateOps.preview(POSITIVE_AGE_PRISM);
      Kind<StateKind.Witness<Person>, Optional<Integer>> result =
          program.foldMap(interpreter, monad);
      StateTuple<Person, Optional<Integer>> tuple = STATE.runState(result, zeroPerson);
      assertThat(tuple.value()).isEmpty();
    }

    @Test
    @DisplayName("GetState returns the whole state")
    void getStateReturnsWholeState() {
      Free<StateOpKind.Witness<Person>, Person> program = StateOps.getState();
      Kind<StateKind.Witness<Person>, Person> result = program.foldMap(interpreter, monad);
      StateTuple<Person, Person> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo(INITIAL_PERSON);
      assertThat(tuple.state()).isEqualTo(INITIAL_PERSON);
    }

    @Test
    @DisplayName("TraverseOver modifies all targets of a Traversal")
    void traverseOverModifiesAll() {
      // Compose TAGS_LENS with LIST_TRAVERSAL to get a Traversal<Person, String>
      Traversal<Person, String> tagsTraversal = TAGS_LENS.andThen(LIST_TRAVERSAL);
      Free<StateOpKind.Witness<Person>, Person> program =
          StateOps.traverseOver(tagsTraversal, String::toUpperCase);
      Kind<StateKind.Witness<Person>, Person> result = program.foldMap(interpreter, monad);
      StateTuple<Person, Person> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.state().tags()).containsExactly("ADMIN", "USER");
      assertThat(tuple.value().tags()).containsExactly("ADMIN", "USER");
    }

    @Test
    @DisplayName("Chained operations: view then over then view")
    void chainedOperations() {
      Free<StateOpKind.Witness<Person>, String> program =
          StateOps.<Person, String>view(NAME_GETTER)
              .flatMap(
                  _ ->
                      StateOps.<Person, String>over(NAME_LENS, String::toUpperCase)
                          .flatMap(_ -> StateOps.view(NAME_GETTER)));

      Kind<StateKind.Witness<Person>, String> result = program.foldMap(interpreter, monad);
      StateTuple<Person, String> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo("ALICE");
      assertThat(tuple.state().name()).isEqualTo("ALICE");
    }

    @Test
    @DisplayName("Assign then view returns the assigned value")
    void assignThenViewRoundTrips() {
      Free<StateOpKind.Witness<Person>, String> program =
          StateOps.<Person, String>assign(NAME_LENS, "Carol")
              .flatMap(_ -> StateOps.view(NAME_GETTER));

      Kind<StateKind.Witness<Person>, String> result = program.foldMap(interpreter, monad);
      StateTuple<Person, String> tuple = STATE.runState(result, INITIAL_PERSON);
      assertThat(tuple.value()).isEqualTo("Carol");
    }

    @Test
    @DisplayName("apply rejects null")
    void applyRejectsNull() {
      assertThatThrownBy(() -> interpreter.apply(null)).isInstanceOf(NullPointerException.class);
    }
  }

  // ===== IOStateOpInterpreter: interpret into IO =====

  @Nested
  @DisplayName("IOStateOpInterpreter (IO monad)")
  class IOInterpreterTests {

    @Test
    @DisplayName("View reads value from AtomicReference")
    void viewReadsFromRef() {
      AtomicReference<Person> ref = new AtomicReference<>(INITIAL_PERSON);
      IOStateOpInterpreter<Person> interpreter = new IOStateOpInterpreter<>(ref);

      Free<StateOpKind.Witness<Person>, String> program = StateOps.view(NAME_GETTER);
      Kind<IOKind.Witness, String> result = program.foldMap(interpreter, IOMonad.INSTANCE);

      String value = IO_OP.narrow(result).unsafeRunSync();
      assertThat(value).isEqualTo("Alice");
      assertThat(ref.get()).isEqualTo(INITIAL_PERSON); // unchanged
    }

    @Test
    @DisplayName("Over modifies AtomicReference state")
    void overModifiesRef() {
      AtomicReference<Person> ref = new AtomicReference<>(INITIAL_PERSON);
      IOStateOpInterpreter<Person> interpreter = new IOStateOpInterpreter<>(ref);

      Free<StateOpKind.Witness<Person>, String> program =
          StateOps.over(NAME_LENS, String::toUpperCase);
      Kind<IOKind.Witness, String> result = program.foldMap(interpreter, IOMonad.INSTANCE);

      String value = IO_OP.narrow(result).unsafeRunSync();
      assertThat(value).isEqualTo("ALICE");
      assertThat(ref.get().name()).isEqualTo("ALICE");
    }

    @Test
    @DisplayName("Assign updates AtomicReference state")
    void assignUpdatesRef() {
      AtomicReference<Person> ref = new AtomicReference<>(INITIAL_PERSON);
      IOStateOpInterpreter<Person> interpreter = new IOStateOpInterpreter<>(ref);

      Free<StateOpKind.Witness<Person>, String> program = StateOps.assign(NAME_LENS, "Bob");
      Kind<IOKind.Witness, String> result = program.foldMap(interpreter, IOMonad.INSTANCE);

      String value = IO_OP.narrow(result).unsafeRunSync();
      assertThat(value).isEqualTo("Bob");
      assertThat(ref.get().name()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("GetState reads whole state from AtomicReference")
    void getStateReadsRef() {
      AtomicReference<Person> ref = new AtomicReference<>(INITIAL_PERSON);
      IOStateOpInterpreter<Person> interpreter = new IOStateOpInterpreter<>(ref);

      Free<StateOpKind.Witness<Person>, Person> program = StateOps.getState();
      Kind<IOKind.Witness, Person> result = program.foldMap(interpreter, IOMonad.INSTANCE);

      Person value = IO_OP.narrow(result).unsafeRunSync();
      assertThat(value).isEqualTo(INITIAL_PERSON);
    }

    @Test
    @DisplayName("Chained IO operations thread state correctly")
    void chainedIOOperations() {
      AtomicReference<Person> ref = new AtomicReference<>(INITIAL_PERSON);
      IOStateOpInterpreter<Person> interpreter = new IOStateOpInterpreter<>(ref);

      Free<StateOpKind.Witness<Person>, String> program =
          StateOps.<Person, String>assign(NAME_LENS, "Dave")
              .flatMap(_ -> StateOps.view(NAME_GETTER));

      Kind<IOKind.Witness, String> result = program.foldMap(interpreter, IOMonad.INSTANCE);
      String value = IO_OP.narrow(result).unsafeRunSync();

      assertThat(value).isEqualTo("Dave");
      assertThat(ref.get().name()).isEqualTo("Dave");
    }

    @Test
    @DisplayName("Constructor rejects null stateRef")
    void constructorRejectsNull() {
      assertThatThrownBy(() -> new IOStateOpInterpreter<>(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Bound =====

  @Nested
  @DisplayName("StateOps.Bound")
  class BoundTests {

    private static final Inject<
            StateOpKind.Witness<Person>,
            EitherFKind.Witness<StateOpKind.Witness<Person>, IdentityKind.Witness>>
        INJECT = InjectInstances.injectLeft();

    private static final EitherFFunctor<StateOpKind.Witness<Person>, IdentityKind.Witness> FUNCTOR =
        EitherFFunctor.of(StateOpFunctor.<Person>instance(), IdentityMonad.INSTANCE);

    private final StateOps.Bound<
            Person, EitherFKind.Witness<StateOpKind.Witness<Person>, IdentityKind.Witness>>
        bound = StateOps.boundTo(INJECT, FUNCTOR);

    @Test
    @DisplayName("boundTo creates Bound instance")
    void boundToCreatesBound() {
      assertThat(bound).isNotNull();
    }

    @Test
    @DisplayName("Bound.view(Getter) translates into combined effect type")
    void boundViewGetterTranslates() {
      var program = bound.view(NAME_GETTER);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound.view(Lens) translates into combined effect type")
    void boundViewLensTranslates() {
      var program = bound.view(NAME_LENS);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound.over translates into combined effect type")
    void boundOverTranslates() {
      var program = bound.over(NAME_LENS, String::toUpperCase);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound.assign translates into combined effect type")
    void boundAssignTranslates() {
      var program = bound.assign(NAME_LENS, "Bob");
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound.preview translates into combined effect type")
    void boundPreviewTranslates() {
      var program = bound.preview(POSITIVE_AGE_PRISM);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound.traverseOver translates into combined effect type")
    void boundTraverseOverTranslates() {
      Traversal<Person, String> tagsTraversal = TAGS_LENS.andThen(LIST_TRAVERSAL);
      var program = bound.traverseOver(tagsTraversal, String::toUpperCase);
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound.getState translates into combined effect type")
    void boundGetStateTranslates() {
      var program = bound.getState();
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("Bound constructor rejects null inject")
    void boundRejectsNullInject() {
      assertThatThrownBy(() -> StateOps.boundTo(null, FUNCTOR))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Bound constructor rejects null functor")
    void boundRejectsNullFunctor() {
      assertThatThrownBy(() -> StateOps.boundTo(INJECT, null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
