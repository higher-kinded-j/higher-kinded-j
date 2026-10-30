// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists the classes that name a type class among their direct interfaces, read from the class files
 * of the jar or directory an instance loads from, so a page's table of instances can be checked for
 * completeness. No class is loaded. A class that reaches the type class only through a
 * sub-interface is missed, as {@code EitherMonad} reaches {@code Monad} through {@code MonadError},
 * so use it only for a type class that no other type class extends, such as {@code Bifunctor} or
 * {@code Profunctor}.
 */
public final class TypeClassInstances {

  private TypeClassInstances() {}

  /**
   * The simple names of the classes beside {@code anInstance} that name {@code typeClass} among
   * their direct interfaces.
   */
  public static List<String> implementingDirectly(Class<?> typeClass, Class<?> anInstance) {
    ClassDesc wanted = ClassDesc.of(typeClass.getName());
    try {
      Path root = Path.of(anInstance.getProtectionDomain().getCodeSource().getLocation().toURI());
      try (FileSystem jar = Files.isDirectory(root) ? null : FileSystems.newFileSystem(root);
          Stream<Path> files = Files.walk(jar == null ? root : jar.getPath("/"))) {
        return files
            .filter(file -> file.getFileName() != null)
            .filter(file -> file.getFileName().toString().endsWith(".class"))
            .map(TypeClassInstances::parse)
            .filter(
                model ->
                    model.interfaces().stream().anyMatch(entry -> entry.asSymbol().equals(wanted)))
            .map(model -> model.thisClass().asSymbol().displayName())
            .sorted()
            .toList();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ClassModel parse(Path file) {
    try {
      return ClassFile.of().parse(Files.readAllBytes(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
