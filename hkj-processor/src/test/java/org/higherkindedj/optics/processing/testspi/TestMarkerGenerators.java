// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.testspi;

import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * Test-only {@link TraversableGenerator} implementations registered via a test-scope {@code
 * META-INF/services} file. Each generator only {@code supports()} a unique marker type that no
 * production fixture or golden file uses, so their registration is additive and inert for all
 * existing tests.
 *
 * <p>Every generator's {@code generateModifyF} body throws naming the generator's simple name, so a
 * generated traversal compiles and the source names which generator won the resolution.
 *
 * <p>The {@code Pri}, {@code Fb} and {@code Box} generators name an optic, read from a factory on
 * the marker type itself ({@code Pri.affine()}, {@code Fb.each()}), so they take part in Focus
 * widening; a test that compiles one of those markers under {@code @GenerateFocus} declares the
 * factories. The {@code Dup} and {@code Solo} generators name none, so their containers stay out of
 * Focus widening.
 *
 * <ul>
 *   <li>{@code com.example.hkjtest.Dup}: supported by three generators (two of equal priority, one
 *       lower) to exercise the equal-priority conflict warning and the lower-priority skip arm.
 *   <li>{@code com.example.hkjtest.Pri}: supported by a default-priority generator registered first
 *       and a {@code PRIORITY_OVERRIDE} generator registered last, exercising priority beating
 *       registration order.
 *   <li>{@code com.example.hkjtest.Fb}: supported by a {@code PRIORITY_FALLBACK} generator
 *       registered first and a default-priority generator registered later, exercising a fallback
 *       losing to a later default.
 *   <li>{@code com.example.hkjtest.Solo}: a ZERO_OR_ONE generator with an empty optic expression,
 *       exercising a container left out of Focus widening.
 *   <li>{@code com.example.hkjtest.Blank}: a ZERO_OR_ONE generator whose optic expression is only
 *       whitespace, which names no optic either.
 *   <li>{@code com.example.hkjtest.Shd}: a default-priority generator that names an optic, shadowed
 *       by a {@code PRIORITY_OVERRIDE} one that names none, so the field is left out of Focus
 *       widening rather than falling through to the lower-priority optic.
 *   <li>{@code com.example.hkjtest.Box}: a generator whose focus type argument index (1) exceeds
 *       the marker's single type argument, exercising the not-enough-type-arguments guard.
 *   <li>type variables named {@code TRAVMARKER}: exercising the neither-array-nor-declared guard.
 * </ul>
 */
public final class TestMarkerGenerators {

  private TestMarkerGenerators() {}

  private static boolean isMarker(TypeMirror type, String fqn) {
    return type instanceof DeclaredType declaredType
        && declaredType.asElement() instanceof TypeElement typeElement
        && typeElement.getQualifiedName().contentEquals(fqn);
  }

  private abstract static class MarkerGeneratorBase implements TraversableGenerator {
    private final String markerFqn;

    MarkerGeneratorBase(String markerFqn) {
      this.markerFqn = markerFqn;
    }

    /** The marker type this generator supports. */
    String markerFqn() {
      return markerFqn;
    }

    @Override
    public boolean supports(TypeMirror type) {
      return isMarker(type, markerFqn);
    }

    @Override
    public CodeBlock generateModifyF(
        RecordComponentElement component,
        ClassName recordClassName,
        List<? extends RecordComponentElement> allComponents) {
      // Compiles in any modifyF and names the winning generator in the generated source.
      return CodeBlock.of(
          "throw new $T($S);", UnsupportedOperationException.class, getClass().getSimpleName());
    }
  }

  /**
   * A marker generator that names an optic: the marker type's own {@code affine()} for a {@code
   * ZERO_OR_ONE} generator, and its {@code each()} otherwise.
   */
  private abstract static class OpticMarkerGeneratorBase extends MarkerGeneratorBase {
    OpticMarkerGeneratorBase(String markerFqn) {
      super(markerFqn);
    }

    @Override
    public String generateOpticExpression() {
      String simpleName = markerFqn().substring(markerFqn().lastIndexOf('.') + 1);
      return simpleName + (getCardinality() == Cardinality.ZERO_OR_ONE ? ".affine()" : ".each()");
    }

    @Override
    public Set<String> getRequiredImports() {
      return Set.of(markerFqn());
    }
  }

  /** First equal-priority generator for the {@code Dup} marker. */
  public static final class DupGeneratorAlpha extends MarkerGeneratorBase {
    /** Creates the generator. */
    public DupGeneratorAlpha() {
      super("com.example.hkjtest.Dup");
    }
  }

  /** Second equal-priority generator for the {@code Dup} marker. */
  public static final class DupGeneratorBeta extends MarkerGeneratorBase {
    /** Creates the generator. */
    public DupGeneratorBeta() {
      super("com.example.hkjtest.Dup");
    }
  }

  /** Lower-priority generator for the {@code Dup} marker. */
  public static final class DupGeneratorFallback extends MarkerGeneratorBase {
    /** Creates the generator. */
    public DupGeneratorFallback() {
      super("com.example.hkjtest.Dup");
    }

    @Override
    public int priority() {
      return -50;
    }
  }

  /** Default-priority generator for the {@code Pri} marker, registered first. */
  public static final class PriDefaultGenerator extends OpticMarkerGeneratorBase {
    /** Creates the generator. */
    public PriDefaultGenerator() {
      super("com.example.hkjtest.Pri");
    }
  }

  /** {@code PRIORITY_OVERRIDE} generator for the {@code Pri} marker, registered last. */
  public static final class PriOverrideGenerator extends OpticMarkerGeneratorBase {
    /** Creates the generator. */
    public PriOverrideGenerator() {
      super("com.example.hkjtest.Pri");
    }

    @Override
    public int priority() {
      return PRIORITY_OVERRIDE;
    }

    @Override
    public Cardinality getCardinality() {
      return Cardinality.ZERO_OR_ONE;
    }
  }

  /** {@code PRIORITY_FALLBACK} generator for the {@code Fb} marker, registered first. */
  public static final class FbFallbackGenerator extends OpticMarkerGeneratorBase {
    /** Creates the generator. */
    public FbFallbackGenerator() {
      super("com.example.hkjtest.Fb");
    }

    @Override
    public int priority() {
      return PRIORITY_FALLBACK;
    }

    @Override
    public Cardinality getCardinality() {
      return Cardinality.ZERO_OR_ONE;
    }
  }

  /** Default-priority generator for the {@code Fb} marker, registered after the fallback. */
  public static final class FbDefaultGenerator extends OpticMarkerGeneratorBase {
    /** Creates the generator. */
    public FbDefaultGenerator() {
      super("com.example.hkjtest.Fb");
    }
  }

  /** ZERO_OR_ONE generator for the {@code Solo} marker with no optic expression. */
  public static final class SoloGenerator extends MarkerGeneratorBase {
    /** Creates the generator. */
    public SoloGenerator() {
      super("com.example.hkjtest.Solo");
    }

    @Override
    public Cardinality getCardinality() {
      return Cardinality.ZERO_OR_ONE;
    }
  }

  /**
   * ZERO_OR_ONE generator for the {@code Blank} marker whose optic expression is only whitespace.
   */
  public static final class BlankGenerator extends MarkerGeneratorBase {
    /** Creates the generator. */
    public BlankGenerator() {
      super("com.example.hkjtest.Blank");
    }

    @Override
    public Cardinality getCardinality() {
      return Cardinality.ZERO_OR_ONE;
    }

    @Override
    public String generateOpticExpression() {
      return "  ";
    }
  }

  /** Default-priority generator for the {@code Shd} marker that names an optic. */
  public static final class ShdDefaultGenerator extends OpticMarkerGeneratorBase {
    /** Creates the generator. */
    public ShdDefaultGenerator() {
      super("com.example.hkjtest.Shd");
    }

    @Override
    public Cardinality getCardinality() {
      return Cardinality.ZERO_OR_ONE;
    }
  }

  /** {@code PRIORITY_OVERRIDE} generator for the {@code Shd} marker that names no optic. */
  public static final class ShdOverrideGenerator extends MarkerGeneratorBase {
    /** Creates the generator. */
    public ShdOverrideGenerator() {
      super("com.example.hkjtest.Shd");
    }

    @Override
    public int priority() {
      return PRIORITY_OVERRIDE;
    }
  }

  /**
   * A marker type's source with the {@code affine()} and {@code each()} factories that an {@link
   * OpticMarkerGeneratorBase} generator names, for a test that compiles it under
   * {@code @GenerateFocus}.
   *
   * @param simpleName the marker's simple name, in package {@code com.example.hkjtest}
   * @return the marker's source
   */
  public static JavaFileObject opticMarker(String simpleName) {
    return JavaFileObjects.forSourceString(
        "com.example.hkjtest." + simpleName,
        """
        package com.example.hkjtest;

        import org.higherkindedj.optics.Affine;
        import org.higherkindedj.optics.Each;

        /** A marker container whose generator names its optic through these factories. */
        public class %s<T> {
          public static <S, A> Affine<S, A> affine() {
            throw new UnsupportedOperationException();
          }

          public static <S, A> Each<S, A> each() {
            throw new UnsupportedOperationException();
          }
        }
        """
            .formatted(simpleName));
  }

  /** Generator for the {@code Box} marker focusing on a type argument index that never exists. */
  public static final class BoxIndexOneGenerator extends OpticMarkerGeneratorBase {
    /** Creates the generator. */
    public BoxIndexOneGenerator() {
      super("com.example.hkjtest.Box");
    }

    @Override
    public int getFocusTypeArgumentIndex() {
      return 1;
    }
  }

  /** Generator that supports type variables named {@code TRAVMARKER}. */
  public static final class TypeVariableGenerator implements TraversableGenerator {
    /** Creates the generator. */
    public TypeVariableGenerator() {}

    @Override
    public boolean supports(TypeMirror type) {
      return type.getKind() == TypeKind.TYPEVAR && type.toString().equals("TRAVMARKER");
    }

    @Override
    public CodeBlock generateModifyF(
        RecordComponentElement component,
        ClassName recordClassName,
        List<? extends RecordComponentElement> allComponents) {
      return CodeBlock.of("");
    }
  }
}
