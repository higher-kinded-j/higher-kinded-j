// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.kind;

import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.annotations.KindSemantics;

/**
 * Registry of known Kind witness types and their corresponding Traverse instances.
 *
 * <p>This registry provides the mapping between Higher-Kinded-J library witness types and their
 * Traverse implementations. The Focus processor uses this registry to automatically generate
 * appropriate {@code traverseOver()} calls for recognised Kind fields.
 *
 * <h2>Supported Types</h2>
 *
 * <table border="1">
 *   <caption>Library Type Mappings</caption>
 *   <tr><th>Witness Type</th><th>Traverse Instance</th><th>Semantics</th></tr>
 *   <tr><td>ListKind.Witness</td><td>ListTraverse.INSTANCE</td><td>ZERO_OR_MORE</td></tr>
 *   <tr><td>MaybeKind.Witness</td><td>MaybeTraverse.INSTANCE</td><td>ZERO_OR_ONE</td></tr>
 *   <tr><td>OptionalKind.Witness</td><td>OptionalTraverse.INSTANCE</td><td>ZERO_OR_ONE</td></tr>
 *   <tr><td>StreamKind.Witness</td><td>StreamTraverse.INSTANCE</td><td>ZERO_OR_MORE</td></tr>
 *   <tr><td>TryKind.Witness</td><td>TryTraverse.INSTANCE</td><td>ZERO_OR_ONE</td></tr>
 *   <tr><td>IdKind.Witness</td><td>IdTraverse.INSTANCE</td><td>EXACTLY_ONE</td></tr>
 *   <tr><td>EitherKind.Witness</td><td>EitherTraverse.instance()</td><td>ZERO_OR_ONE</td></tr>
 *   <tr><td>ValidatedKind.Witness</td><td>ValidatedTraverse.instance()</td><td>ZERO_OR_ONE</td></tr>
 * </table>
 *
 * <h2>Extensibility</h2>
 *
 * <p>For custom Kind types not in this registry, users can annotate fields with {@link
 * org.higherkindedj.optics.annotations.TraverseField} to provide explicit Traverse configuration.
 *
 * @see KindFieldAnalyser
 * @see org.higherkindedj.optics.annotations.TraverseField
 */
public final class KindRegistry {

  /** Base package for Higher-Kinded-J HKT types. */
  public static final String HKT_PACKAGE = "org.higherkindedj.hkt";

  /** The Kind interface fully qualified name. */
  public static final String KIND_INTERFACE = HKT_PACKAGE + ".Kind";

  /**
   * Represents a registered Kind type mapping.
   *
   * @param traverseExpression the code to obtain the Traverse instance
   * @param semantics the cardinality semantics
   * @param isParameterised whether this witness type requires type parameters
   */
  public record KindMapping(
      String traverseExpression, KindSemantics semantics, boolean isParameterised) {

    /** Creates a mapping for a non-parameterised type with an INSTANCE field. */
    static KindMapping instance(String traverseClass, KindSemantics semantics) {
      return new KindMapping(traverseClass + ".INSTANCE", semantics, false);
    }

    /** Creates a mapping for a parameterised type with a factory method. */
    static KindMapping factory(String traverseClass, KindSemantics semantics) {
      return new KindMapping(traverseClass + ".instance()", semantics, true);
    }
  }

  /**
   * Map from witness type qualified name to its Traverse mapping.
   *
   * <p>The keys are the fully qualified names of the Witness inner classes, without the type
   * parameters.
   */
  private static final Map<String, KindMapping> KNOWN_KINDS =
      Map.ofEntries(
          // List - zero or more elements
          Map.entry(
              HKT_PACKAGE + ".list.ListKind.Witness",
              KindMapping.instance(HKT_PACKAGE + ".list.ListTraverse", KindSemantics.ZERO_OR_MORE)),

          // Maybe - zero or one element
          Map.entry(
              HKT_PACKAGE + ".maybe.MaybeKind.Witness",
              KindMapping.instance(
                  HKT_PACKAGE + ".maybe.MaybeTraverse", KindSemantics.ZERO_OR_ONE)),

          // Optional - zero or one element
          Map.entry(
              HKT_PACKAGE + ".optional.OptionalKind.Witness",
              KindMapping.instance(
                  HKT_PACKAGE + ".optional.OptionalTraverse", KindSemantics.ZERO_OR_ONE)),

          // Stream - zero or more elements
          Map.entry(
              HKT_PACKAGE + ".stream.StreamKind.Witness",
              KindMapping.instance(
                  HKT_PACKAGE + ".stream.StreamTraverse", KindSemantics.ZERO_OR_MORE)),

          // Try - zero or one element (success case)
          Map.entry(
              HKT_PACKAGE + ".trymonad.TryKind.Witness",
              KindMapping.instance(
                  HKT_PACKAGE + ".trymonad.TryTraverse", KindSemantics.ZERO_OR_ONE)),

          // Id - exactly one element
          Map.entry(
              HKT_PACKAGE + ".id.IdKind.Witness",
              KindMapping.instance(HKT_PACKAGE + ".id.IdTraverse", KindSemantics.EXACTLY_ONE)),

          // Either - zero or one element (right-biased, parameterised)
          Map.entry(
              HKT_PACKAGE + ".either.EitherKind.Witness",
              KindMapping.factory(
                  HKT_PACKAGE + ".either.EitherTraverse", KindSemantics.ZERO_OR_ONE)),

          // Validated - zero or one element (valid case, parameterised)
          Map.entry(
              HKT_PACKAGE + ".validated.ValidatedKind.Witness",
              KindMapping.factory(
                  HKT_PACKAGE + ".validated.ValidatedTraverse", KindSemantics.ZERO_OR_ONE)));

  private KindRegistry() {
    // Utility class
  }

  /**
   * Looks up a mapping for the given witness type.
   *
   * @param witnessQualifiedName the fully qualified name of the witness type, without type
   *     parameters
   * @return an Optional containing the mapping if found, empty otherwise
   */
  public static Optional<KindMapping> lookup(String witnessQualifiedName) {
    return Optional.ofNullable(KNOWN_KINDS.get(witnessQualifiedName));
  }

  /**
   * Checks if a qualified name refers to the Kind interface.
   *
   * @param qualifiedName the type's qualified name
   * @return true if this is the Kind interface
   */
  public static boolean isKindInterface(String qualifiedName) {
    return KIND_INTERFACE.equals(qualifiedName);
  }

  /**
   * Checks if a witness type is from the Higher-Kinded-J library.
   *
   * @param witnessQualifiedName the witness type's qualified name
   * @return true if this is a library witness type
   */
  public static boolean isLibraryWitness(String witnessQualifiedName) {
    return witnessQualifiedName.startsWith(HKT_PACKAGE + ".");
  }

  /**
   * Extracts the base witness type name from a potentially parameterised type.
   *
   * <p>For example, "org.higherkindedj.hkt.either.EitherKind.Witness&lt;String&gt;" becomes
   * "org.higherkindedj.hkt.either.EitherKind.Witness".
   *
   * @param witnessType the full witness type string
   * @return the base type without parameters
   */
  public static String extractBaseWitnessType(String witnessType) {
    int angleBracket = witnessType.indexOf('<');
    if (angleBracket > 0) {
      return witnessType.substring(0, angleBracket);
    }
    return witnessType;
  }

  /**
   * Extracts type arguments from a parameterised witness type.
   *
   * <p>For example, "EitherKind.Witness&lt;String&gt;" returns "String".
   *
   * @param witnessType the full witness type string
   * @return the type arguments, or empty string if none
   */
  public static String extractWitnessTypeArgs(String witnessType) {
    int start = witnessType.indexOf('<');
    int end = witnessType.lastIndexOf('>');
    if (start > 0 && end > start) {
      return witnessType.substring(start + 1, end);
    }
    return "";
  }
}
