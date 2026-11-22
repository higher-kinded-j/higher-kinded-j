// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;

/**
 * Writes an annotation the client carries, or inherits, into generated source as it was written.
 * javapoet's {@link AnnotationSpec#get(AnnotationMirror)} leaves out an array written empty, which
 * changes the annotation where the member's default is not empty, and does not compile where the
 * member has no default. This keeps it, at any depth.
 */
final class AnnotationSpecs {

  private AnnotationSpecs() {
    // Utility class - prevent instantiation
  }

  /** The annotation as written, each member in the order it was written. */
  static AnnotationSpec copyOf(AnnotationMirror annotation) {
    // javapoet writes each value, one block per array element; only an empty array and a nested
    // annotation, which may hold one, are written here instead.
    Map<String, List<CodeBlock>> written = AnnotationSpec.get(annotation).members();
    AnnotationSpec.Builder copy =
        AnnotationSpec.builder(
            ClassName.get((TypeElement) annotation.getAnnotationType().asElement()));
    annotation
        .getElementValues()
        .forEach(
            (member, value) -> {
              String name = member.getSimpleName().toString();
              List<CodeBlock> blocks = written.getOrDefault(name, List.of());
              value.accept(
                  new SimpleAnnotationValueVisitor14<Void, Void>() {
                    @Override
                    protected Void defaultAction(Object unusedValue, Void unused) {
                      copy.addMember(name, blockOf(value, blocks.getFirst()));
                      return null;
                    }

                    @Override
                    public Void visitArray(List<? extends AnnotationValue> values, Void unused) {
                      if (values.isEmpty()) {
                        copy.addMember(name, "{}");
                      }
                      for (int i = 0; i < values.size(); i++) {
                        copy.addMember(name, blockOf(values.get(i), blocks.get(i)));
                      }
                      return null;
                    }
                  },
                  null);
            });
    return copy.build();
  }

  /** One value as written: javapoet's block, or a nested annotation copied by {@link #copyOf}. */
  private static CodeBlock blockOf(AnnotationValue value, CodeBlock written) {
    return value.accept(
        new SimpleAnnotationValueVisitor14<CodeBlock, Void>(written) {
          @Override
          public CodeBlock visitAnnotation(AnnotationMirror nested, Void unused) {
            return CodeBlock.of("$L", copyOf(nested));
          }
        },
        null);
  }
}
