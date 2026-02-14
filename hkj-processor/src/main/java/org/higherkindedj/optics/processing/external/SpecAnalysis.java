// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Result of analysing a spec interface extending {@code OpticsSpec<S>}.
 *
 * <p>The analysis captures information needed to generate optics from the spec interface:
 *
 * <ul>
 *   <li>The source type {@code S} from {@code OpticsSpec<S>}
 *   <li>Abstract methods requiring generation (annotated with copy strategies)
 *   <li>Default methods to copy unchanged
 *   <li>The target package for generated code
 * </ul>
 *
 * @param specInterface the analysed spec interface element
 * @param sourceType the source type {@code S} from {@code OpticsSpec<S>}
 * @param sourceTypeElement the source type as a TypeElement (for generating code)
 * @param opticMethods abstract methods that define optics to generate
 * @param defaultMethods default methods to copy into the generated class
 */
public record SpecAnalysis(
    TypeElement specInterface,
    TypeMirror sourceType,
    TypeElement sourceTypeElement,
    List<OpticMethodInfo> opticMethods,
    List<ExecutableElement> defaultMethods) {

  /** The kind of optic defined by a method. */
  public enum OpticKind {
    LENS,
    PRISM,
    TRAVERSAL,
    AFFINE,
    ISO,
    GETTER,
    FOLD
  }

  /** The copy strategy specified by an annotation. */
  public enum CopyStrategyKind {
    VIA_BUILDER,
    WITHER,
    VIA_CONSTRUCTOR,
    VIA_COPY_AND_SET,
    NONE // For prisms and traversals that don't need copy strategies
  }

  /** The prism matching strategy specified by an annotation. */
  public enum PrismHintKind {
    INSTANCE_OF,
    MATCH_WHEN,
    NONE
  }

  /** The traversal strategy specified by an annotation. */
  public enum TraversalHintKind {
    TRAVERSE_WITH,
    THROUGH_FIELD,
    NONE
  }

  /**
   * Information about an abstract method defining an optic.
   *
   * @param method the abstract method element
   * @param opticKind the kind of optic (Lens, Prism, Traversal, etc.)
   * @param focusType the focus type {@code A} in {@code Lens<S, A>}
   * @param copyStrategy the copy strategy for lenses
   * @param copyStrategyInfo parsed annotation values for the copy strategy
   * @param prismHint the prism hint for prisms
   * @param prismHintInfo parsed annotation values for the prism hint
   * @param traversalHint the traversal hint for traversals
   * @param traversalHintInfo parsed annotation values for the traversal hint
   */
  public record OpticMethodInfo(
      ExecutableElement method,
      OpticKind opticKind,
      TypeMirror focusType,
      CopyStrategyKind copyStrategy,
      CopyStrategyInfo copyStrategyInfo,
      PrismHintKind prismHint,
      PrismHintInfo prismHintInfo,
      TraversalHintKind traversalHint,
      TraversalHintInfo traversalHintInfo) {

    /**
     * Returns the method name.
     *
     * @return the simple name of the method
     */
    public String methodName() {
      return method.getSimpleName().toString();
    }

    /**
     * Returns whether this optic requires a copy strategy annotation.
     *
     * @return true if this is a lens that needs a copy strategy
     */
    public boolean requiresCopyStrategy() {
      return opticKind == OpticKind.LENS;
    }

    /**
     * Returns whether this optic requires a prism hint annotation.
     *
     * @return true if this is a prism that needs a hint
     */
    public boolean requiresPrismHint() {
      return opticKind == OpticKind.PRISM;
    }

    /**
     * Returns whether this optic requires a traversal hint annotation.
     *
     * @return true if this is a traversal that needs a hint
     */
    public boolean requiresTraversalHint() {
      return opticKind == OpticKind.TRAVERSAL;
    }
  }

  /**
   * Parsed values from a copy strategy annotation.
   *
   * @param getter the getter method name on source (for @ViaBuilder)
   * @param toBuilder the toBuilder method name (for @ViaBuilder)
   * @param setter the setter method name (for @ViaBuilder, @ViaCopyAndSet)
   * @param build the build method name (for @ViaBuilder)
   * @param witherMethod the wither method name (for @Wither)
   * @param parameterOrder the constructor parameter order (for @ViaConstructor)
   * @param copyConstructor the copy constructor type (for @ViaCopyAndSet)
   */
  public record CopyStrategyInfo(
      String getter,
      String toBuilder,
      String setter,
      String build,
      String witherMethod,
      String[] parameterOrder,
      String copyConstructor) {

    /** Creates an empty copy strategy info. */
    public static CopyStrategyInfo empty() {
      return new CopyStrategyInfo("", "", "", "", "", new String[0], "");
    }

    /** Creates info for @ViaBuilder annotation. */
    public static CopyStrategyInfo forBuilder(
        String getter, String toBuilder, String setter, String build) {
      return new CopyStrategyInfo(getter, toBuilder, setter, build, "", new String[0], "");
    }

    /** Creates info for @Wither annotation. */
    public static CopyStrategyInfo forWither(String getter, String witherMethod) {
      return new CopyStrategyInfo(getter, "", "", "", witherMethod, new String[0], "");
    }

    /** Creates info for @ViaConstructor annotation. */
    public static CopyStrategyInfo forConstructor(String[] parameterOrder) {
      return new CopyStrategyInfo("", "", "", "", "", parameterOrder, "");
    }

    /** Creates info for @ViaCopyAndSet annotation. */
    public static CopyStrategyInfo forCopyAndSet(String copyConstructor, String setter) {
      return new CopyStrategyInfo("", "", setter, "", "", new String[0], copyConstructor);
    }
  }

  /**
   * Parsed values from a prism hint annotation.
   *
   * @param targetType the target subtype (for @InstanceOf)
   * @param predicate the predicate method name (for @MatchWhen)
   * @param getter the getter method name (for @MatchWhen)
   */
  public record PrismHintInfo(TypeMirror targetType, String predicate, String getter) {

    /** Creates an empty prism hint info. */
    public static PrismHintInfo empty() {
      return new PrismHintInfo(null, "", "");
    }

    /** Creates info for @InstanceOf annotation. */
    public static PrismHintInfo forInstanceOf(TypeMirror targetType) {
      return new PrismHintInfo(targetType, "", "");
    }

    /** Creates info for @MatchWhen annotation. */
    public static PrismHintInfo forMatchWhen(String predicate, String getter) {
      return new PrismHintInfo(null, predicate, getter);
    }
  }

  /**
   * Parsed values from a traversal hint annotation.
   *
   * @param traversalReference the traversal reference (for @TraverseWith)
   * @param fieldName the field name (for @ThroughField)
   * @param fieldTraversal the explicit traversal for the field (for @ThroughField)
   */
  public record TraversalHintInfo(
      String traversalReference, String fieldName, String fieldTraversal) {

    /** Creates an empty traversal hint info. */
    public static TraversalHintInfo empty() {
      return new TraversalHintInfo("", "", "");
    }

    /** Creates info for @TraverseWith annotation. */
    public static TraversalHintInfo forTraverseWith(String traversalReference) {
      return new TraversalHintInfo(traversalReference, "", "");
    }

    /** Creates info for @ThroughField annotation. */
    public static TraversalHintInfo forThroughField(String fieldName, String traversal) {
      return new TraversalHintInfo("", fieldName, traversal);
    }
  }
}
