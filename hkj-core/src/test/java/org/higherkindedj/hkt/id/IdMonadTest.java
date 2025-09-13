// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Identity Monad (Id) Tests")
class IdMonadTest {

  private IdMonad idMonad;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
  }

  // Minimal imposter Kind for testing ClassCastException in ID.narrow
  private static class ImposterKind<A> implements Kind<Id.Witness, A> {
    public ImposterKind(A value) {}
  }

  @Nested
  @DisplayName("Id Record Tests (formerly Id Class Tests)")
  class IdRecordTests { // Renamed to reflect Id is now a record

    @Test
    @DisplayName("of() should create Id with given value")
    void of_createsIdWithValue() {
      Id<String> id1 = Id.of("test");
      assertThat(id1.value()).isEqualTo("test");

      Id<Integer> id2 = Id.of(123);
      assertThat(id2.value()).isEqualTo(123);
    }

    @Test
    @DisplayName("of() should create Id with null value")
    void of_createsIdWithNull() {
      Id<String> idNull = Id.of(null);
      assertThat(idNull.value()).isNull();
    }

    @Test
    @DisplayName("record accessor value() should return the wrapped value")
    void recordAccessor_value_returnsWrappedValue() { // Test the record's accessor
      assertThat(Id.of("hello").value()).isEqualTo("hello");
      assertThat(Id.of((Object) null).value()).isNull();
    }

    @Test
    @DisplayName("record-generated equals() and hashCode() should follow contract")
    void recordGenerated_equalsAndHashCode() { // Test record's generated methods
      Id<String> id1a = Id.of("one");
      Id<String> id1b = Id.of("one"); // Records are value objects
      Id<String> id2 = Id.of("two");
      Id<String> idNull1 = Id.of(null);
      Id<String> idNull2 = Id.of(null);

      // Reflexive
      assertThat(id1a.equals(id1a)).isTrue();
      assertThat(idNull1.equals(idNull1)).isTrue();

      // Symmetric
      assertThat(id1a.equals(id1b)).isTrue();
      assertThat(id1b.equals(id1a)).isTrue();
      assertThat(idNull1.equals(idNull2)).isTrue();
      assertThat(idNull2.equals(idNull1)).isTrue();

      // Non-nullity
      assertThat(id1a.equals(null)).isFalse();
      assertThat(idNull1.equals(null)).isFalse();

      // Different values
      assertThat(id1a.equals(id2)).isFalse();
      assertThat(id1a.equals(idNull1)).isFalse();
      assertThat(idNull1.equals(id1a)).isFalse();

      // HashCode
      assertThat(id1a.hashCode()).isEqualTo(id1b.hashCode());
      assertThat(idNull1.hashCode()).isEqualTo(idNull2.hashCode());
    }

    @Test
    @DisplayName("record-generated toString() should return correct format")
    void recordGenerated_toString_returnsCorrectFormat() { // Test record's generated method
      assertThat(Id.of("test").toString()).isEqualTo("Id[value=test]"); // Common record format
      assertThat(Id.of(123).toString()).isEqualTo("Id[value=123]");
      assertThat(Id.of(null).toString()).isEqualTo("Id[value=null]");
    }

    @Test
    @DisplayName("instance map() should transform value")
    void instanceMap_transformsValue() {
      Id<Integer> idInt = Id.of(5);
      Id<String> idString = idInt.map(i -> "Val:" + i);
      assertThat(idString.value()).isEqualTo("Val:5");
    }

    @Test
    @DisplayName("instance map() with null value should apply function to null")
    void instanceMap_withNullValue() {
      Id<Object> idNull = Id.of(null);
      Id<String> idString = idNull.map(o -> "WasNull");
      assertThat(idString.value()).isEqualTo("WasNull");
    }

    @Test
    @DisplayName("instance map() should throw NPE for null function")
    void instanceMap_throwsNPEForNullFunction() {
      Id<Integer> idInt = Id.of(5);
      assertThatThrownBy(() -> idInt.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function cannot be null");
    }

    @Test
    @DisplayName("instance flatMap() should apply function returning Id")
    void instanceFlatMap_appliesFunctionReturningId() {
      Id<Integer> idInt = Id.of(5);
      Id<String> idString = idInt.flatMap(i -> Id.of("FlatVal:" + (i * 2)));
      assertThat(idString.value()).isEqualTo("FlatVal:10");
    }

    @Test
    @DisplayName("instance flatMap() should throw if function returns null Id")
    void instanceFlatMap_throwsIfFunctionReturnsNullId() {
      Id<Integer> idInt = Id.of(5);
      assertThatThrownBy(() -> idInt.flatMap(i -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function returned by flatMap cannot be null");
    }

    @Test
    @DisplayName("instance flatMap() should throw NPE for null function")
    void instanceFlatMap_throwsNPEForNullFunction() {
      Id<Integer> idInt = Id.of(5);
      assertThatThrownBy(() -> idInt.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function cannot be null");
    }

    @Test
    @DisplayName("Witness class can be instantiated (for coverage)")
    void witness_canBeInstantiated() {
      // This test is primarily for JaCoCo coverage of the Witness inner class,
      Id.Witness witnessInstance = new Id.Witness();
      assertThat(witnessInstance).isNotNull();
    }
  }

  @Nested
  @DisplayName("IdKindHelper Tests")
  class IdKindHelperTests {
    @Test
    @DisplayName("narrow() should cast Kind to Id for correct type")
    void narrow_castsKindToId() {
      Kind<Id.Witness, String> kind = Id.of("test");
      Id<String> id = ID.narrow(kind);
      assertThat(id.value()).isEqualTo("test");
      assertThat(id).isInstanceOf(Id.class);
    }

    @Test
    @DisplayName(
        "narrow() should throw KindUnwrapException for wrong Kind implementation but correct"
            + " Witness")
    void narrow_throwsCKindImplementation() {
      Kind<Id.Witness, String> imposterKind = new ImposterKind<>("imposter");
      assertThatThrownBy(() -> ID.narrow(imposterKind)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("narrow() should throw NullPointerException if kind is null")
    void narrow_throwsNullPointerExceptionIfKindIsNull() {
      assertThatThrownBy(() -> ID.narrow(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Id Kind for narrow cannot be null");
    }

    @Test
    @DisplayName("widen() should cast Id to Kind")
    void widen_castsIdToKind() {
      Id<String> id = Id.of("test");
      Kind<Id.Witness, String> kind = ID.widen(id);
      assertThat(kind).isSameAs(id);
      assertThat(ID.narrow(kind).value()).isEqualTo("test");
    }

    @Test
    @DisplayName("widen() should throw NullPointerException if id is null")
    void widen_throwsNullPointerExceptionIfIdIsNull() {
      assertThatThrownBy(() -> ID.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input Id cannot be null");
    }

    @Test
    @DisplayName("unwrap() should return value from Kind")
    void unwrap_returnsValueFromKind() {
      Kind<Id.Witness, String> kind = Id.of("test");
      assertThat(ID.unwrap(kind)).isEqualTo("test");

      Kind<Id.Witness, Integer> kindNull = Id.of(null);
      assertThat(ID.unwrap(kindNull)).isNull();
    }

    @Test
    @DisplayName("unwrap() should throw NullPointerException if kind is null")
    void unwrap_throwsNPEIfKindIsNull() {
      assertThatThrownBy(() -> ID.unwrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Id Kind for narrow cannot be null");
    }

    @Test
    @DisplayName("unwrap() should throw ClassCastException for wrong Kind implementation")
    void unwrap_throwsKindUnwrapExceptionForWrongKindImplementation() {
      Kind<Id.Witness, String> imposterKind = new ImposterKind<>("imposter");
      assertThatThrownBy(() -> ID.unwrap(imposterKind)).isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("IdentityMonad Instance Tests")
  class IdMonadInstanceTests {

    @Test
    @DisplayName("instance() should return singleton")
    void instance_returnsSingleton() {
      assertThat(IdMonad.instance()).isSameAs(IdMonad.instance());
    }

    @Test
    @DisplayName("of() should wrap value in Id Kind")
    void of_wrapsValueInIdKind() {
      Kind<Id.Witness, String> kind = idMonad.of("hello");
      assertThat(ID.narrow(kind).value()).isEqualTo("hello");

      Kind<Id.Witness, Object> kindNull = idMonad.of(null);
      assertThat(ID.narrow(kindNull).value()).isNull();
    }

    @Test
    @DisplayName("map() should transform value within Id Kind")
    void map_transformsValueInIdKind() {
      Kind<Id.Witness, Integer> kindInt = idMonad.of(10);
      Kind<Id.Witness, String> kindString = idMonad.map(i -> "Num:" + i, kindInt);
      assertThat(ID.narrow(kindString).value()).isEqualTo("Num:10");
    }

    @Test
    @DisplayName("map() with null original value")
    void map_withNullOriginalValue() {
      Kind<Id.Witness, String> kindNull = idMonad.of(null);
      Kind<Id.Witness, String> kindString = idMonad.map(s -> "WasNull", kindNull);
      assertThat(ID.narrow(kindString).value()).isEqualTo("WasNull");
    }

    @Test
    @DisplayName("map() should throw NPE for null function")
    void map_throwsNPEForNullFunction() {
      Kind<Id.Witness, Integer> kindInt = idMonad.of(10);
      assertThatThrownBy(() -> idMonad.map(null, kindInt))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function f for map cannot be null");
    }

    @Test
    @DisplayName("map() should throw NPE for null Kind")
    void map_throwsNPEForNullKind() {
      assertThatThrownBy(() -> idMonad.map(i -> "test", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("source Kind for map cannot be null");
    }

    @Test
    @DisplayName("ap() should apply wrapped function to wrapped value")
    void ap_appliesWrappedFunction() {
      Kind<Id.Witness, Function<Integer, String>> kindFn = idMonad.of(i -> "Res:" + (i * 2));
      Kind<Id.Witness, Integer> kindVal = idMonad.of(5);
      Kind<Id.Witness, String> result = idMonad.ap(kindFn, kindVal);
      assertThat(ID.narrow(result).value()).isEqualTo("Res:10");
    }

    @Test
    @DisplayName("ap() should throw NullPointerException if wrapped function is null")
    void ap_throwsIfWrappedFunctionIsNull() {
      Kind<Id.Witness, Function<Integer, String>> kindFnNull = idMonad.of(null);
      Kind<Id.Witness, Integer> kindVal = idMonad.of(5);
      assertThatThrownBy(() -> idMonad.ap(kindFnNull, kindVal))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function wrapped in Id cannot be null for ap");
    }

    @Test
    @DisplayName("ap() should throw NPE for null function Kind")
    void ap_throwsNPEForNullFunctionKind() {
      Kind<Id.Witness, Integer> kindVal = idMonad.of(5);
      assertThatThrownBy(() -> idMonad.ap(null, kindVal))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function Kind for ap cannot be null");
    }

    @Test
    @DisplayName("ap() should throw NPE for null value Kind")
    void ap_throwsNPEForNullValueKind() {
      Kind<Id.Witness, Function<Integer, String>> kindFn = idMonad.of(i -> "test");
      assertThatThrownBy(() -> idMonad.ap(kindFn, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("argument Kind for ap cannot be null");
    }

    @Test
    @DisplayName("flatMap() should apply function returning Id Kind")
    void flatMap_appliesFunctionReturningIdKind() {
      Kind<Id.Witness, Integer> kindInt = idMonad.of(7);
      Kind<Id.Witness, String> result = idMonad.flatMap(i -> Id.of("Flat:" + (i + 3)), kindInt);
      assertThat(ID.narrow(result).value()).isEqualTo("Flat:10");
    }

    @Test
    @DisplayName("flatMap() should throw NullPointerException if function returns null Kind")
    void flatMap_throwsIfFunctionReturnsNullKind() {
      Kind<Id.Witness, Integer> kindInt = idMonad.of(7);
      Function<Integer, Kind<Id.Witness, String>> fnReturningNull = i -> null;
      assertThatThrownBy(() -> idMonad.flatMap(fnReturningNull, kindInt))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function passed to flatMap returned null Kind");
    }

    @Test
    @DisplayName("flatMap() should throw NPE for null function")
    void flatMap_throwsNPEForNullFunction() {
      Kind<Id.Witness, Integer> kindInt = idMonad.of(7);
      assertThatThrownBy(() -> idMonad.flatMap(null, kindInt))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function f for flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap() should throw NPE for null Kind")
    void flatMap_throwsNPEForNullKind() {
      Function<Integer, Kind<Id.Witness, String>> fn = i -> Id.of("test");
      assertThatThrownBy(() -> idMonad.flatMap(fn, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("source Kind for flatMap cannot be null");
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {
    private Integer testValue;
    private Kind<Id.Witness, Integer> m;
    private Function<Integer, Kind<Id.Witness, String>> f;
    private Function<String, Kind<Id.Witness, String>> g;

    @BeforeEach
    void setUpLaws() {
      testValue = 42;
      m = idMonad.of(testValue);
      f = i -> idMonad.of("f(" + i + ")");
      g = s -> idMonad.of("g(" + s + ")");
    }

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<Id.Witness, String> leftSide = idMonad.flatMap(f, idMonad.of(testValue));
      Kind<Id.Witness, String> rightSide = f.apply(testValue);

      assertThat(ID.unwrap(leftSide)).isEqualTo(ID.unwrap(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Kind<Id.Witness, Integer> leftSide = idMonad.flatMap(idMonad::of, m);

      assertThat(ID.unwrap(leftSide)).isEqualTo(ID.unwrap(m));
      assertThat(leftSide).isEqualTo(m);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<Id.Witness, String> leftSide = idMonad.flatMap(g, idMonad.flatMap(f, m));
      Kind<Id.Witness, String> rightSide = idMonad.flatMap(x -> idMonad.flatMap(g, f.apply(x)), m);
      assertThat(ID.unwrap(leftSide)).isEqualTo(ID.unwrap(rightSide));
    }
  }
}
