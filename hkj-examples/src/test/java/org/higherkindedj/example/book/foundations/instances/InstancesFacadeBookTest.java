// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.instances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.context;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.lazy;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.reader;
import static org.higherkindedj.hkt.instances.Witnesses.state;
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.instances.Witnesses.trampoline;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.instances.Witnesses;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Obtaining Instances page makes about the {@code Instances} facade. */
@DisplayName("Obtaining Instances: the Instances facade")
class InstancesFacadeBookTest {

  @Test
  @DisplayName("monad, applicative and functor return the one canonical object")
  void oneObjectForThreeLevels() {
    Object monad = Instances.monad(maybe());

    assertThat(Instances.applicative(maybe())).isSameAs(monad);
    assertThat(Instances.functor(maybe())).isSameAs(monad);
    assertThat(monad).isSameAs(MaybeMonad.INSTANCE);
  }

  @Test
  @DisplayName("every token the page lists gives a Monad, and the page lists every token")
  void everyTokenIsListed() {
    List<Witnesses.Of<?>> listed =
        List.of(
            maybe(),
            io(),
            list(),
            nonEmptyList(),
            optional(),
            try_(),
            vtask(),
            vstream(),
            lazy(),
            stream(),
            completableFuture(),
            trampoline(),
            id(),
            either(),
            reader(),
            context(),
            state());

    listed.forEach(token -> assertThat(Instances.monad(token)).isNotNull());
    assertThat(publicStaticMethodsOf(Witnesses.class)).hasSize(listed.size());
  }

  @Test
  @DisplayName("the page names every method on Instances")
  void everyMethodIsNamed() {
    assertThat(publicStaticMethodsOf(Instances.class))
        .containsExactlyInAnyOrder(
            "monad",
            "applicative",
            "functor",
            "monadError",
            "monadZero",
            "alternative",
            "validated",
            "eitherOrBoth",
            "writer",
            "eitherT",
            "maybeT",
            "optionalT",
            "readerT",
            "stateT",
            "writerT");
  }

  /** The page's capability table: each token with monadError, and its error type. */
  private static final Map<String, String> MONAD_ERROR =
      Map.of(
          "maybe", "Unit",
          "optional", "Unit",
          "try_", "Throwable",
          "completableFuture", "Throwable",
          "vtask", "Throwable",
          "either", "L");

  /** The page's capability table: the tokens with monadZero and alternative. */
  private static final Set<String> MONAD_ZERO = Set.of("maybe", "optional", "list", "stream");

  /** The page's token table: the tokens with a type parameter. */
  private static final Set<String> GENERIC_TOKENS = Set.of("either", "reader", "context", "state");

  @Test
  @DisplayName("the capability and token tables match every token's instance exactly")
  void tablesMatchEveryToken() throws Exception {
    for (Method token : Witnesses.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(token.getModifiers()) || !Modifier.isStatic(token.getModifiers())) {
        continue;
      }
      String name = token.getName();
      Class<?> instance = Instances.monad((Witnesses.Of<?>) token.invoke(null)).getClass();

      assertThat(MonadError.class.isAssignableFrom(instance))
          .as("%s has monadError", name)
          .isEqualTo(MONAD_ERROR.containsKey(name));
      if (MONAD_ERROR.containsKey(name)) {
        assertThat(errorTypeOf(instance))
            .as("%s's error type", name)
            .isEqualTo(MONAD_ERROR.get(name));
      }
      assertThat(MonadZero.class.isAssignableFrom(instance))
          .as("%s has monadZero", name)
          .isEqualTo(MONAD_ZERO.contains(name));
      assertThat(Alternative.class.isAssignableFrom(instance))
          .as("%s has alternative", name)
          .isEqualTo(MONAD_ZERO.contains(name));
      assertThat(token.getTypeParameters().length > 0)
          .as("%s has a type parameter", name)
          .isEqualTo(GENERIC_TOKENS.contains(name));
    }
  }

  @Test
  @DisplayName("asking for a capability the instance lacks fails at run time, at the call")
  void missingCapabilityFailsAtRunTime() {
    assertThatThrownBy(() -> Instances.<EitherKind.Witness<String>>monadZero(either()))
        .isInstanceOf(ClassCastException.class);
    assertThatThrownBy(() -> Instances.monadError(io())).isInstanceOf(ClassCastException.class);
  }

  @Test
  @DisplayName("monadError does not check E: a wrong one compiles, and fails when it is used")
  void errorTypeIsNotChecked() {
    // Maybe's error type is Unit; String is accepted all the same
    MonadError<MaybeKind.Witness, String> wrong = Instances.monadError(maybe());

    assertThat(wrong).isSameAs(MaybeMonad.INSTANCE);
    assertThatThrownBy(() -> wrong.raiseError("refused")).isInstanceOf(ClassCastException.class);
  }

  /** The second type argument of the MonadError a class implements: a class name or a variable. */
  private static String errorTypeOf(Class<?> instance) {
    for (Class<?> c = instance; c != null; c = c.getSuperclass()) {
      for (Type t : c.getGenericInterfaces()) {
        if (t instanceof ParameterizedType p && p.getRawType() == MonadError.class) {
          Type e = p.getActualTypeArguments()[1];
          return e instanceof Class<?> k ? k.getSimpleName() : e.getTypeName();
        }
      }
    }
    throw new AssertionError(instance + " does not implement MonadError directly");
  }

  private static List<String> publicStaticMethodsOf(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
        .map(Method::getName)
        .distinct()
        .toList();
  }
}
