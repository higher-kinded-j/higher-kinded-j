// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static org.junit.jupiter.api.Assertions.*;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.basejdk.ArrayGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.MapValueGenerator;
import org.higherkindedj.optics.processing.generator.hkj.EitherGenerator;
import org.higherkindedj.optics.processing.generator.hkj.ValidatedGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the defensive fallback paths in type-extraction methods across all generators. These
 * fallbacks are unreachable through the normal annotation processor pipeline because the
 * TraversalProcessor validates type arguments before calling generateModifyF(). We exercise them
 * directly using proxy-based mocks to verify the defensive code is correct and to achieve full
 * coverage.
 */
@DisplayName("Type extraction fallback paths")
class TypeExtractionFallbackTest {

  private static final ClassName DUMMY_CLASS = ClassName.get("com.example", "Dummy");

  // ── Mock helpers ──────────────────────────────────────────────────────────

  /** Creates a Name proxy that wraps a simple string. */
  private static Name nameOf(final String value) {
    return proxy(
        Name.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "toString" -> value;
              case "length" -> value.length();
              case "charAt" -> value.charAt((int) args[0]);
              case "subSequence" -> value.subSequence((int) args[0], (int) args[1]);
              case "contentEquals" -> value.contentEquals((CharSequence) args[0]);
              case "hashCode" -> value.hashCode();
              case "equals" -> value.equals(args[0] == null ? null : args[0].toString());
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  /** Creates a RecordComponentElement with the given name and type. */
  private static RecordComponentElement componentOf(final String name, final TypeMirror type) {
    return proxy(
        RecordComponentElement.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "asType" -> type;
              case "getSimpleName" -> nameOf(name);
              case "toString" -> name;
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  /** Creates a DeclaredType with the given type arguments and optional element. */
  private static DeclaredType declaredType(
      final Element element, final List<? extends TypeMirror> typeArgs) {
    return proxy(
        DeclaredType.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getTypeArguments" -> typeArgs;
              case "asElement" -> element;
              case "getKind" -> TypeKind.DECLARED;
              case "toString" -> "MockDeclaredType";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  /** Creates a simple TypeMirror that is NOT a DeclaredType or ArrayType. */
  private static TypeMirror nonDeclaredType() {
    return proxy(
        TypeMirror.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getKind" -> TypeKind.INT;
              case "toString" -> "int";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> throw new UnsupportedOperationException(method.getName());
            });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(final Class<T> iface, final InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, handler);
  }

  // ── Concrete subclass of BaseTraversableGenerator for testing protected methods ──

  private static BaseTraversableGenerator baseGenerator() {
    return new BaseTraversableGenerator() {
      @Override
      public boolean supports(final TypeMirror type) {
        return false;
      }

      @Override
      public String generateOpticExpression() {
        return "";
      }

      @Override
      public Set<String> getRequiredImports() {
        return Set.of();
      }

      @Override
      public CodeBlock generateModifyF(
          final RecordComponentElement component,
          final ClassName recordClassName,
          final List<? extends RecordComponentElement> allComponents) {
        return CodeBlock.builder().build();
      }
    };
  }

  // ── Tests ─────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("BaseTraversableGenerator.getGenericTypeName")
  class BaseTraversableGeneratorFallbacks {

    @Test
    @DisplayName("returns Object for non-DeclaredType component")
    void returnsObjectForNonDeclaredType() {
      var gen = baseGenerator();
      var component = componentOf("field", nonDeclaredType());
      assertEquals(ClassName.get(Object.class), gen.getGenericTypeName(component));
    }

    @Test
    @DisplayName("returns Object for DeclaredType with empty type arguments")
    void returnsObjectForEmptyTypeArgs() {
      var gen = baseGenerator();
      var component = componentOf("field", declaredType(null, List.of()));
      assertEquals(ClassName.get(Object.class), gen.getGenericTypeName(component));
    }
  }

  @Nested
  @DisplayName("ArrayGenerator fallbacks")
  class ArrayGeneratorFallbacks {

    @Test
    @DisplayName("generateModifyF falls back to Object for non-ArrayType component")
    void fallsBackToObjectForNonArrayType() {
      var gen = new ArrayGenerator();
      var component = componentOf("items", nonDeclaredType());
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }
  }

  @Nested
  @DisplayName("EitherGenerator fallbacks")
  class EitherGeneratorFallbacks {

    @Test
    @DisplayName("supports returns false when element is null")
    void supportsReturnsFalseForNullElement() {
      var gen = new EitherGenerator();
      var type = declaredType(null, List.of());
      assertFalse(gen.supports(type));
    }

    @Test
    @DisplayName("generateModifyF falls back to Object for DeclaredType with no type arguments")
    void fallsBackToObjectForEmptyTypeArgs() {
      var gen = new EitherGenerator();
      var component = componentOf("data", declaredType(null, List.of()));
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }

    @Test
    @DisplayName("generateModifyF falls back to Object for non-DeclaredType component")
    void fallsBackToObjectForNonDeclaredType() {
      var gen = new EitherGenerator();
      var component = componentOf("data", nonDeclaredType());
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }
  }

  @Nested
  @DisplayName("ValidatedGenerator fallbacks")
  class ValidatedGeneratorFallbacks {

    @Test
    @DisplayName("generateModifyF falls back to Object for DeclaredType with no type arguments")
    void fallsBackToObjectForEmptyTypeArgs() {
      var gen = new ValidatedGenerator();
      var component = componentOf("data", declaredType(null, List.of()));
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }

    @Test
    @DisplayName("generateModifyF falls back to Object for non-DeclaredType component")
    void fallsBackToObjectForNonDeclaredType() {
      var gen = new ValidatedGenerator();
      var component = componentOf("data", nonDeclaredType());
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }
  }

  @Nested
  @DisplayName("MapValueGenerator fallbacks")
  class MapValueGeneratorFallbacks {

    @Test
    @DisplayName("generateModifyF falls back to Object for DeclaredType with no type arguments")
    void fallsBackToObjectForEmptyTypeArgs() {
      var gen = new MapValueGenerator();
      var component = componentOf("data", declaredType(null, List.of()));
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }

    @Test
    @DisplayName("generateModifyF falls back to Object for non-DeclaredType component")
    void fallsBackToObjectForNonDeclaredType() {
      var gen = new MapValueGenerator();
      var component = componentOf("data", nonDeclaredType());
      var code = gen.generateModifyF(component, DUMMY_CLASS, List.of(component));
      assertNotNull(code);
      assertTrue(code.toString().contains("Object"));
    }
  }
}
