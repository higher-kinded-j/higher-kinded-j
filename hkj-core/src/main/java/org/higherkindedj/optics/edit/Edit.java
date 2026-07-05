// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.edit;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.Nullable;

/**
 * A single pure edit at one optic path: a write that always succeeds.
 *
 * <p>Edits are the leaves of a multi-edit operation. Each factory pairs an optic (a {@link
 * FocusPath} or a {@link Setter}) with an incoming value or function:
 *
 * <ul>
 *   <li>{@link #set(FocusPath, Object) set} — write a value;
 *   <li>{@link #modify(FocusPath, Function) modify} — transform the current value;
 *   <li>{@link #setIfPresent(FocusPath, Object) setIfPresent} — write, or do nothing when the
 *       incoming value is absent ({@code null});
 *   <li>{@link #modifyIfPresent(FocusPath, Object, BiFunction) modifyIfPresent} — combine an
 *       incoming value with the current one, or do nothing when absent;
 *   <li>{@link #parseIfPresent(FocusPath, Object, Function) parseIfPresent} — parse the incoming
 *       value first; this is the fallible factory and returns a {@link FallibleEdit}.
 * </ul>
 *
 * <p>The {@code …IfPresent} factories treat {@code null} as <em>absent</em>: the edit contributes
 * the identity update, so sparse request DTOs land one-to-one with no {@code if} ceremony. This
 * also means a sparse edit cannot <em>clear</em> a field — absent and "set to null" are
 * deliberately the same, in keeping with non-null domain models. The user functions given to {@code
 * modifyIfPresent} and {@code parseIfPresent} are never invoked with {@code null}.
 *
 * <p>Combine pure edits with {@link Edits#combine} (compile-time purity: a {@link FallibleEdit}
 * does not fit), or mix them with fallible edits in {@link Edits#accumulate}.
 *
 * @param <S> the type of the value being edited
 * @see FallibleEdit
 * @see Edits
 */
public sealed interface Edit<S> extends FallibleEdit<S> permits Edit.Infallible {

  /**
   * The write this edit performs.
   *
   * <p>For {@code modify}-style edits the function reads the <em>current</em> value at application
   * time, so under {@link Edits#combine} or a valid {@link Edits#accumulate} the writes see each
   * other's results in left-to-right order.
   *
   * @return the update (non-null)
   */
  Update<S> toUpdate();

  @Override
  default Validated<NonEmptyList<FieldError>, Update<S>> toValidated() {
    return Validated.valid(toUpdate());
  }

  /**
   * {@inheritDoc}
   *
   * <p>A pure edit carries no errors, so this is a no-op returning {@code this}.
   */
  @Override
  default Edit<S> at(String label) {
    Objects.requireNonNull(label, "label must not be null");
    return this;
  }

  /**
   * The infallible leaf edit wrapping its write: a pure edit that always succeeds.
   *
   * <p>Created by the {@code Edit} factories; not usually named directly.
   *
   * @param update the write; never null
   * @param <S> the type of the value being edited
   */
  record Infallible<S>(Update<S> update) implements Edit<S> {

    /**
     * Canonical constructor; validates.
     *
     * @throws NullPointerException if {@code update} is null
     */
    public Infallible {
      Objects.requireNonNull(update, "update must not be null");
    }

    @Override
    public Update<S> toUpdate() {
      return update;
    }
  }

  /**
   * An edit that writes {@code value} at {@code path}.
   *
   * @param path the target path; must not be null
   * @param value the value to write; must not be null — absence is expressed with {@link
   *     #setIfPresent(FocusPath, Object)}
   * @param <S> the type of the value being edited
   * @param <A> the type at the path
   * @return the edit (non-null)
   * @throws NullPointerException if {@code path} or {@code value} is null
   */
  static <S, A> Edit<S> set(FocusPath<S, A> path, A value) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(value, "value must not be null; absence is expressed with setIfPresent");
    return writeEdit(path::set, value);
  }

  /**
   * An edit that writes {@code value} through {@code setter}.
   *
   * @param setter the target setter; must not be null
   * @param value the value to write; must not be null — absence is expressed with {@link
   *     #setIfPresent(Setter, Object)}
   * @param <S> the type of the value being edited
   * @param <A> the type the setter focuses
   * @return the edit (non-null)
   * @throws NullPointerException if {@code setter} or {@code value} is null
   */
  static <S, A> Edit<S> set(Setter<S, A> setter, A value) {
    Objects.requireNonNull(setter, "setter must not be null");
    Objects.requireNonNull(value, "value must not be null; absence is expressed with setIfPresent");
    return writeEdit(setter::set, value);
  }

  /**
   * An edit that transforms the current value at {@code path} with {@code fn}.
   *
   * @param path the target path; must not be null
   * @param fn the transformation; must not be null
   * @param <S> the type of the value being edited
   * @param <A> the type at the path
   * @return the edit (non-null)
   * @throws NullPointerException if {@code path} or {@code fn} is null
   */
  static <S, A> Edit<S> modify(FocusPath<S, A> path, Function<A, A> fn) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(fn, "fn must not be null");
    return modifyEdit(path::modify, fn);
  }

  /**
   * An edit that transforms the focused value through {@code setter} with {@code fn}.
   *
   * @param setter the target setter; must not be null
   * @param fn the transformation; must not be null
   * @param <S> the type of the value being edited
   * @param <A> the type the setter focuses
   * @return the edit (non-null)
   * @throws NullPointerException if {@code setter} or {@code fn} is null
   */
  static <S, A> Edit<S> modify(Setter<S, A> setter, Function<A, A> fn) {
    Objects.requireNonNull(setter, "setter must not be null");
    Objects.requireNonNull(fn, "fn must not be null");
    return modifyEdit(setter::modify, fn);
  }

  /**
   * An edit that writes {@code value} at {@code path}, or does nothing when {@code value} is absent
   * ({@code null}).
   *
   * <p>The absent case contributes the identity update — the sparse-PATCH building block.
   *
   * @param path the target path; must not be null
   * @param value the value to write, or {@code null} for no-op
   * @param <S> the type of the value being edited
   * @param <A> the type at the path
   * @return the edit (non-null)
   * @throws NullPointerException if {@code path} is null
   */
  static <S, A> Edit<S> setIfPresent(FocusPath<S, A> path, @Nullable A value) {
    Objects.requireNonNull(path, "path must not be null");
    return value == null ? noOp() : set(path, value);
  }

  /**
   * An edit that writes {@code value} through {@code setter}, or does nothing when {@code value} is
   * absent ({@code null}).
   *
   * @param setter the target setter; must not be null
   * @param value the value to write, or {@code null} for no-op
   * @param <S> the type of the value being edited
   * @param <A> the type the setter focuses
   * @return the edit (non-null)
   * @throws NullPointerException if {@code setter} is null
   */
  static <S, A> Edit<S> setIfPresent(Setter<S, A> setter, @Nullable A value) {
    Objects.requireNonNull(setter, "setter must not be null");
    return value == null ? noOp() : set(setter, value);
  }

  /**
   * An edit that combines an incoming {@code value} with the current value at {@code path}, or does
   * nothing when {@code value} is absent ({@code null}).
   *
   * <p>{@code fn} receives {@code (incoming, current)} and is never invoked with {@code null}. The
   * current value is read at <em>application</em> time.
   *
   * <pre>{@code
   * Edit.modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta)
   * }</pre>
   *
   * @param path the target path; must not be null
   * @param value the incoming value, or {@code null} for no-op
   * @param fn combines (incoming, current) into the new value; must not be null
   * @param <S> the type of the value being edited
   * @param <A> the type at the path
   * @param <B> the incoming value's type
   * @return the edit (non-null)
   * @throws NullPointerException if {@code path} or {@code fn} is null
   */
  static <S, A, B> Edit<S> modifyIfPresent(
      FocusPath<S, A> path, @Nullable B value, BiFunction<? super B, ? super A, ? extends A> fn) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(fn, "fn must not be null");
    return value == null ? noOp() : modifyEdit(path::modify, current -> fn.apply(value, current));
  }

  /**
   * An edit that combines an incoming {@code value} with the focused value through {@code setter},
   * or does nothing when {@code value} is absent ({@code null}).
   *
   * @param setter the target setter; must not be null
   * @param value the incoming value, or {@code null} for no-op
   * @param fn combines (incoming, current) into the new value; must not be null
   * @param <S> the type of the value being edited
   * @param <A> the type the setter focuses
   * @param <B> the incoming value's type
   * @return the edit (non-null)
   * @throws NullPointerException if {@code setter} or {@code fn} is null
   */
  static <S, A, B> Edit<S> modifyIfPresent(
      Setter<S, A> setter, @Nullable B value, BiFunction<? super B, ? super A, ? extends A> fn) {
    Objects.requireNonNull(setter, "setter must not be null");
    Objects.requireNonNull(fn, "fn must not be null");
    return value == null ? noOp() : modifyEdit(setter::modify, current -> fn.apply(value, current));
  }

  /**
   * A fallible edit that parses the incoming {@code raw} value and, if it parses, writes the result
   * at {@code path}; when {@code raw} is absent ({@code null}) the edit does nothing and the parser
   * is <em>not</em> invoked.
   *
   * <p>Parse failures are located with {@link FallibleEdit#at(String)}:
   *
   * <pre>{@code
   * Edit.parseIfPresent(EMAIL, req.email(), Email::parse).at("email")
   * }</pre>
   *
   * @param path the target path; must not be null
   * @param raw the incoming raw value, or {@code null} for no-op
   * @param parser parses the raw value; must not be null and must not return null
   * @param <S> the type of the value being edited
   * @param <A> the type at the path
   * @param <B> the raw incoming type
   * @return the fallible edit (non-null)
   * @throws NullPointerException if {@code path} or {@code parser} is null, or the parser returns
   *     null
   */
  static <S, A, B> FallibleEdit<S> parseIfPresent(
      FocusPath<S, A> path,
      @Nullable B raw,
      Function<? super B, Validated<NonEmptyList<FieldError>, A>> parser) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(parser, "parser must not be null");
    if (raw == null) {
      return noOp();
    }
    Validated<NonEmptyList<FieldError>, A> parsed =
        Objects.requireNonNull(parser.apply(raw), "parser must not return null");
    return parsedEdit(path::set, parsed);
  }

  /**
   * A fallible edit that parses the incoming {@code raw} value and, if it parses, writes the result
   * through {@code setter}; when {@code raw} is absent ({@code null}) the edit does nothing and the
   * parser is <em>not</em> invoked.
   *
   * @param setter the target setter; must not be null
   * @param raw the incoming raw value, or {@code null} for no-op
   * @param parser parses the raw value; must not be null and must not return null
   * @param <S> the type of the value being edited
   * @param <A> the type the setter focuses
   * @param <B> the raw incoming type
   * @return the fallible edit (non-null)
   * @throws NullPointerException if {@code setter} or {@code parser} is null, or the parser returns
   *     null
   */
  static <S, A, B> FallibleEdit<S> parseIfPresent(
      Setter<S, A> setter,
      @Nullable B raw,
      Function<? super B, Validated<NonEmptyList<FieldError>, A>> parser) {
    Objects.requireNonNull(setter, "setter must not be null");
    Objects.requireNonNull(parser, "parser must not be null");
    if (raw == null) {
      return noOp();
    }
    Validated<NonEmptyList<FieldError>, A> parsed =
        Objects.requireNonNull(parser.apply(raw), "parser must not return null");
    return parsedEdit(setter::set, parsed);
  }

  // Shared bodies: the FocusPath and Setter overloads differ only in the write target.

  private static <S> Edit<S> noOp() {
    return new Infallible<>(Update.identity());
  }

  private static <S, A> Edit<S> writeEdit(BiFunction<A, S, S> write, A value) {
    return new Infallible<>(s -> write.apply(value, s));
  }

  private static <S, A> Edit<S> modifyEdit(
      BiFunction<Function<A, A>, S, S> modifier, Function<A, A> fn) {
    return new Infallible<>(s -> modifier.apply(fn, s));
  }

  private static <S, A> FallibleEdit<S> parsedEdit(
      BiFunction<A, S, S> write, Validated<NonEmptyList<FieldError>, A> parsed) {
    return new FallibleEdit.Parsed<>(parsed.map(a -> s -> write.apply(a, s)));
  }
}
