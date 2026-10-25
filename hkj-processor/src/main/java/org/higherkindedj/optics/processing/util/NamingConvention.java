// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A naming convention for a method that reads or sets one value: its bare name, {@code name}, or
 * the name capitalised after a prefix, {@code getName} or {@code withName}.
 *
 * <p>A diagnostic that names a method the type does not have offers the one it most likely meant. A
 * prefix added, dropped or swapped is a convention rather than a typo, and too many edits away for
 * {@link ProcessorUtils#nearestName}, so the method that spells the same value in the convention
 * the call needs is offered first. What was written may follow any convention; what is offered, the
 * same value or a near miss, follows only this one, since a method of another kind can compile in
 * the call and quietly do something else.
 *
 * <p>{@link #ACCESSOR} is also the order a field's accessor is paired by, {@linkplain #of(String,
 * String) then spelt as a wither writes the field} where {@code @ImportOptics} pairs one with a
 * wither, and {@link #WITHER} what decides which {@code @ViaCopyAndSet} setter has its result
 * handed back and which method {@code @ImportOptics} reads as a wither; the others serve only what
 * a diagnostic offers. {@link #hasPrefix} tells a getter, setter or wither from a word that starts
 * like one, such as {@code withdraw}.
 *
 * @since 0.5.0
 */
public enum NamingConvention {

  /**
   * An accessor: the bare name first, as a record spells it, then {@code getName}, then {@code
   * isName}, the order a field's accessor is looked for in.
   */
  ACCESSOR("", "get", "is"),

  /**
   * A setter that sets the copy it is called on, as {@code @ViaCopyAndSet} calls one: {@code
   * setName} only. A bare or a {@code with} method may hand back a new value instead, and
   * {@code @Wither} rebuilds through one of those without a copy.
   */
  SETTER("set"),

  /**
   * A builder's setter, whose result the chain carries on with: the bare name first, as a fluent
   * builder spells it, then {@code setName}, as a protobuf builder does, then {@code withName}.
   */
  BUILDER_SETTER("", "set", "with"),

  /**
   * A wither: {@code withName} only, since a bare or a {@code set} method may change the value it
   * is called on rather than hand back a copy.
   */
  WITHER("with");

  /** Every prefix a name may be written with, the empty one standing for the bare name. */
  private static final List<String> ANY =
      Stream.of(values()).flatMap(convention -> convention.prefixes.stream()).distinct().toList();

  /** This convention's prefixes, in the order it prefers them; the empty one is the bare name. */
  private final List<String> prefixes;

  NamingConvention(String... prefixes) {
    this.prefixes = List.of(prefixes);
  }

  /**
   * The spellings of {@code name} in this convention, in the order it prefers them.
   *
   * @param name the bare name, a field's or a constructor parameter's; must not be null
   * @return the spellings (non-null)
   */
  public List<String> of(String name) {
    return spellings(Objects.requireNonNull(name, "name"), prefixes);
  }

  /**
   * The spellings of {@code name} in this convention, as {@link #of} gives them, then each prefix
   * before {@code writtenAs}, the name as another method writes it after its own prefix, where that
   * adds a spelling. A wither {@code witheMail} writes {@code eMail} as JavaBeans does, so its
   * accessor is {@code eMail}, {@code getEMail} or {@code isEMail}, then {@code geteMail} or {@code
   * iseMail}; a wither {@code withEMail} writes it as {@link #of} spells it, and adds nothing. The
   * bare name is never respelt.
   *
   * @param name the bare name, a field's; must not be null
   * @param writtenAs the name as another method writes it after its prefix; must not be null
   * @return the spellings (non-null)
   * @since 0.5.1
   */
  public List<String> of(String name, String writtenAs) {
    Objects.requireNonNull(writtenAs, "writtenAs");
    return Stream.concat(
            of(name).stream(),
            prefixes.stream().filter(prefix -> !prefix.isEmpty()).map(prefix -> prefix + writtenAs))
        .distinct()
        .toList();
  }

  /**
   * Whether this convention spells {@code name}: {@code withName} as a wither, {@code name} or
   * {@code getName} as an accessor. A convention that takes the bare name, as {@link #ACCESSOR} and
   * {@link #BUILDER_SETTER} do, spells every name.
   *
   * @param name the name as written; must not be null
   * @return true when {@code name} is a spelling of this convention's
   */
  public boolean spells(String name) {
    return rank(Objects.requireNonNull(name, "name")) >= 0;
  }

  /**
   * The candidate that spells, in this convention, the value {@code written} spells in any: {@code
   * getName} for {@code name} as an accessor, {@code withName} for {@code setName} as a wither. Of
   * several, the one whose prefix this convention prefers; of those, the first.
   *
   * @param written the name as written; must not be null
   * @param candidates the names the type declares; must not be null
   * @return that candidate, or empty where none spells the same value
   */
  public Optional<String> sameValueAmong(String written, List<String> candidates) {
    Objects.requireNonNull(written, "written");
    return candidates.stream()
        .filter(candidate -> spellsTheSameValue(written, candidate))
        .min(Comparator.comparingInt(this::rank));
  }

  /**
   * The candidate {@code written} most likely meant: the one that {@link #sameValueAmong spells the
   * same value}, else the {@link ProcessorUtils#nearestName nearest} misspelling among the names
   * this convention spells.
   *
   * @param written the name as written; must not be null
   * @param candidates the names the type declares; must not be null
   * @return that candidate, or empty where none is either
   */
  public Optional<String> nearest(String written, List<String> candidates) {
    return sameValueAmong(written, candidates)
        .or(
            () ->
                ProcessorUtils.nearestName(
                    written, candidates.stream().filter(this::spells).toList()));
  }

  /**
   * {@code name} without the prefix this convention spells it with, as {@link #hasPrefix} reads
   * one: {@code Away} for {@code getAway} as an accessor, and the name itself where it is bare or a
   * word, such as {@code getaway}.
   *
   * @param name the name as written; must not be null
   * @return what follows the prefix, or {@code name} where it has none
   */
  public String withoutPrefix(String name) {
    Objects.requireNonNull(name, "name");
    return prefixOf(name, prefixes).map(prefix -> name.substring(prefix.length())).orElse(name);
  }

  /**
   * Whether {@code name} is {@code prefix} followed by a value's name as an accessor spells it: a
   * name capitalising leaves as it is, as {@code getAway} spells {@code away} and {@code get_x}
   * spells {@code _x}, or one whose second letter is a capital, as the JavaBeans convention spells
   * {@code eMail}'s getter {@code geteMail}. A name that runs on in lower case after the prefix is
   * a word instead, such as {@code getaway}, {@code settings} or {@code withdraw}.
   *
   * @param name the name as written; must not be null
   * @param prefix the prefix, such as {@code get} or {@code with}; must not be null, and an empty
   *     one is no prefix
   * @return true when {@code name} is {@code prefix} before a value's name
   */
  public static boolean hasPrefix(String name, String prefix) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(prefix, "prefix");
    if (prefix.isEmpty() || !name.startsWith(prefix) || name.length() == prefix.length()) {
      return false;
    }
    String rest = name.substring(prefix.length());
    return rest.equals(ProcessorUtils.capitalise(rest))
        || (rest.length() > 1 && Character.isUpperCase(rest.charAt(1)));
  }

  /**
   * Whether some name has {@code written} among its spellings in any convention and {@code
   * candidate} among this one's. That name is one of the two, or what follows the prefix {@code
   * written} carries; an empty name spells nothing, or it would be every bare prefix.
   */
  private boolean spellsTheSameValue(String written, String candidate) {
    return Stream.concat(
            Stream.of(written, candidate),
            prefixOf(written, ANY).map(prefix -> written.substring(prefix.length())).stream())
        .filter(root -> !root.isEmpty())
        .anyMatch(
            root ->
                spellings(root, ANY).contains(written)
                    && spellings(root, prefixes).contains(candidate));
  }

  /**
   * Where a name's prefix comes in this convention's order, a bare name's at the empty one; below
   * zero where this convention does not spell a name so.
   */
  private int rank(String candidate) {
    return prefixes.indexOf(prefixOf(candidate, prefixes).orElse(""));
  }

  /** {@code name} spelt after each of {@code prefixes} in turn: bare, or capitalised after one. */
  private static List<String> spellings(String name, List<String> prefixes) {
    return prefixes.stream()
        .map(prefix -> prefix.isEmpty() ? name : prefix + ProcessorUtils.capitalise(name))
        .toList();
  }

  /** The non-empty one of {@code prefixes} a name {@link #hasPrefix has as its prefix}. */
  private static Optional<String> prefixOf(String name, List<String> prefixes) {
    return prefixes.stream().filter(prefix -> hasPrefix(name, prefix)).findFirst();
  }
}
