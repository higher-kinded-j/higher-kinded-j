// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.google.testing.compile.Compilation;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Loads the classes a {@link Compilation} wrote, falling back to the test's own classpath, so that
 * a test can run what the processors generated. The same job is done by {@code
 * RuntimeCompilationHelper} in hkj-processor, which these tests cannot reach: the traversal
 * generators live here, and the module dependency runs the other way.
 */
final class GeneratedClassLoader extends ClassLoader {
  private final Map<String, byte[]> bytecode = new HashMap<>();

  GeneratedClassLoader(final Compilation compilation) {
    super(GeneratedClassLoader.class.getClassLoader());
    for (JavaFileObject file : compilation.generatedFiles()) {
      if (file.getKind() != JavaFileObject.Kind.CLASS) {
        continue;
      }
      String path = file.getName().replace("/" + StandardLocation.CLASS_OUTPUT + "/", "");
      String name = path.substring(0, path.length() - ".class".length()).replace('/', '.');
      bytecode.put(name, read(file));
    }
  }

  /** Loads a generated class, or a class on the test's own classpath, by its binary name. */
  Class<?> load(final String name) throws ClassNotFoundException {
    return loadClass(name);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    byte[] bytes = bytecode.get(name);
    if (bytes == null) {
      throw new ClassNotFoundException(name);
    }
    return defineClass(name, bytes, 0, bytes.length);
  }

  private static byte[] read(final JavaFileObject file) {
    try (InputStream in = file.openInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read " + file.getName(), e);
    }
  }
}
