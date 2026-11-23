// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Reads what javax.lang.model leaves out about a class file: how it retains an annotation type
 * javac could not resolve. It also says whether an element was read from one.
 */
final class ClassFiles {

  private final Elements elements;

  ClassFiles(Elements elements) {
    this.elements = elements;
  }

  /**
   * Whether an element was read from a class file. {@code Elements.getFileObjectOf} may be
   * unsupported outside javac; there the element is taken as source, so a type it names that javac
   * could not resolve is not reported as a missing dependency the processor could not confirm. An
   * annotation naming one waits a round, as one in source does, and the last round reports a client
   * still waiting. hkj-processor's {@code ProcessorUtils.compiledFromSource} makes the opposite
   * choice, reading a type it cannot place as a class file; here waiting, then the report, keeps
   * the client from vanishing either way.
   */
  boolean readFrom(Element element) {
    return classFileOf(element).isPresent();
  }

  /**
   * Whether the class file {@code writer} was read from keeps {@code missing}, an annotation type
   * javac could not resolve, for the compiler alone. Retention belongs to an annotation type, so
   * any one use of it in the file says: one kept among the annotations visible at run time, which
   * Spring reads by reflection, is not; one kept only among those invisible there is. Where the
   * file cannot be read, or names the type nowhere, it is taken as visible.
   */
  boolean keepsForTheCompilerAlone(Element writer, DeclaredType missing) {
    Optional<JavaFileObject> file = classFileOf(writer);
    if (file.isEmpty()) {
      return false;
    }
    String descriptor =
        "L"
            + elements.getBinaryName((TypeElement) missing.asElement()).toString().replace('.', '/')
            + ";";
    try (InputStream in = file.get().openInputStream()) {
      ClassModel model = ClassFile.of().parse(in.readAllBytes());
      List<AttributedElement> parts =
          Stream.concat(
                  Stream.<AttributedElement>of(model),
                  Stream.<AttributedElement>concat(
                      model.methods().stream(), model.fields().stream()))
              .toList();
      return parts.stream()
              .noneMatch(part -> visibleAnnotationsOf(part).anyMatch(descriptor::equals))
          && parts.stream()
              .anyMatch(part -> invisibleAnnotationsOf(part).anyMatch(descriptor::equals));
    } catch (IOException | IllegalArgumentException unreadable) {
      return false;
    }
  }

  private Optional<JavaFileObject> classFileOf(Element element) {
    try {
      return Optional.ofNullable(elements.getFileObjectOf(element))
          .filter(file -> file.getKind() == JavaFileObject.Kind.CLASS);
    } catch (UnsupportedOperationException e) {
      return Optional.empty();
    }
  }

  /** The descriptors of the annotations a part keeps visible at run time, its parameters' too. */
  private static Stream<String> visibleAnnotationsOf(AttributedElement part) {
    return Stream.concat(
            part.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream()),
            part.findAttribute(Attributes.runtimeVisibleParameterAnnotations()).stream()
                .flatMap(attribute -> attribute.parameterAnnotations().stream())
                .flatMap(List::stream))
        .map(ClassFiles::descriptorOf);
  }

  /**
   * The descriptors of the annotations a part keeps for the compiler alone, its parameters' too.
   */
  private static Stream<String> invisibleAnnotationsOf(AttributedElement part) {
    return Stream.concat(
            part.findAttribute(Attributes.runtimeInvisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream()),
            part.findAttribute(Attributes.runtimeInvisibleParameterAnnotations()).stream()
                .flatMap(attribute -> attribute.parameterAnnotations().stream())
                .flatMap(List::stream))
        .map(ClassFiles::descriptorOf);
  }

  private static String descriptorOf(Annotation annotation) {
    return annotation.className().stringValue();
  }
}
