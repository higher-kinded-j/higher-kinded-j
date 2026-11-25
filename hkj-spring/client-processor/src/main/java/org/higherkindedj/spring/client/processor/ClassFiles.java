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
import java.lang.classfile.MethodModel;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Reads what javax.lang.model leaves out about a class file: how it retains an annotation type
 * javac could not resolve, and whether it records a method's parameter names. It also says whether
 * an element was read from one, and whether a generated name would replace a class read from one.
 *
 * <p>hkj-processor's {@code ProcessorUtils} answers the parameter-name, class-file and
 * generated-name questions the same way. They are copied rather than shared because depending on
 * hkj-processor would join it to this processor on the consumer's processor path, as {@link
 * TypeNames} says; if one copy changes, the other should.
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

  /**
   * Whether a method's parameters carry the names its source wrote. javac reads them from source,
   * or from a class file's {@code MethodParameters} attribute, which {@code -parameters} writes, or
   * from the local variable table {@code -g} writes for a method with a body. Where it finds none
   * it names them {@code arg0}, {@code arg1} by position, as it does every parameter of an abstract
   * method compiled without {@code -parameters}. Those names are taken as lost unless the class
   * file records names for every method of that name and parameter count, in case the source wrote
   * them. A class file this cannot read records none. hkj-processor's {@code
   * ProcessorUtils.keepsParameterNames} answers the same question the same way.
   */
  boolean keepsParameterNames(ExecutableElement method) {
    List<? extends VariableElement> parameters = method.getParameters();
    boolean placeholders =
        !parameters.isEmpty()
            && IntStream.range(0, parameters.size())
                .allMatch(
                    index -> parameters.get(index).getSimpleName().contentEquals("arg" + index));
    if (!placeholders) {
      return true;
    }
    String name = method.getSimpleName().toString();
    return classFileOf(method)
        .map(file -> recordsNames(file, name, parameters.size()))
        .orElse(true);
  }

  /**
   * Whether a class file records the parameter names of each method of that name and parameter
   * count, as javac reads them: it passes over a name the file marks mandated, as it marks a
   * compact canonical constructor's, or synthetic, and a record's canonical constructor records
   * names even without {@code -parameters}, so another constructor answers only for itself.
   */
  private static boolean recordsNames(JavaFileObject file, String name, int arity) {
    try (InputStream in = file.openInputStream()) {
      List<MethodModel> candidates =
          ClassFile.of().parse(in.readAllBytes()).methods().stream()
              .filter(method -> method.methodName().equalsString(name))
              .filter(method -> method.methodTypeSymbol().parameterCount() == arity)
              .toList();
      return !candidates.isEmpty() && candidates.stream().allMatch(ClassFiles::namesItsParameters);
    } catch (IOException
        | IllegalArgumentException
        | IllegalStateException
        | UnsupportedOperationException unreadable) {
      return false;
    }
  }

  private static boolean namesItsParameters(MethodModel method) {
    return method
        .findAttribute(Attributes.methodParameters())
        .map(
            attribute ->
                !attribute.parameters().isEmpty()
                    && attribute.parameters().stream()
                        .allMatch(
                            parameter ->
                                parameter.name().isPresent()
                                    && (parameter.flagsMask()
                                            & (ClassFile.ACC_MANDATED | ClassFile.ACC_SYNTHETIC))
                                        == 0))
        .orElse(false);
  }

  /**
   * Whether writing a source named {@code qualifiedName} would replace a class no HKJ generator
   * wrote: one read from a class file that carries no HKJ {@code
   * org.higherkindedj.optics.annotations.Generated}, such as a dependency's, or a hand-written
   * class Gradle's incremental build did not recompile. javac's Filer refuses a name a source of
   * this compilation declares, but writes over a class file with no more than a warning.
   * hkj-processor's {@code ProcessorUtils.replacesUngeneratedClass} answers the same way.
   */
  boolean replacesUngeneratedClass(String qualifiedName) {
    TypeElement taken = elements.getTypeElement(qualifiedName);
    return taken != null
        && classFileOf(taken).isPresent()
        && taken.getAnnotationMirrors().stream()
            .noneMatch(
                mirror ->
                    ((TypeElement) mirror.getAnnotationType().asElement())
                        .getQualifiedName()
                        .contentEquals("org.higherkindedj.optics.annotations.Generated"));
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
