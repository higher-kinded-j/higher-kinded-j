// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import org.jspecify.annotations.Nullable;

/**
 * Runs one program's first use of a class in a class loader that defines this package afresh. A
 * fresh loader gives each order its own class initialisation, which in the shared test JVM happens
 * once, to whichever test gets there first.
 */
final class FreshPackage {

  private FreshPackage() {}

  /**
   * Uses {@code first}, then reads the {@code MAPPER} constant declared on {@code spec}.
   *
   * @param first the simple name of the class the program uses first
   * @param spec the simple name of the spec declaring {@code MAPPER}
   * @return what the constant holds afterwards
   */
  static @Nullable Object mapperAfterFirstUsing(String first, String spec) throws Exception {
    String pkg = FreshPackage.class.getPackageName() + ".";
    ClassLoader parent = FreshPackage.class.getClassLoader();
    ClassLoader fresh =
        new ClassLoader(parent) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(pkg)) {
              return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
              Class<?> loaded = findLoadedClass(name);
              if (loaded != null) {
                return loaded;
              }
              try (InputStream in = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                  throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
              } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
              }
            }
          }
        };
    Class.forName(pkg + first, true, fresh);
    Field mapper = Class.forName(pkg + spec, false, fresh).getField("MAPPER");
    mapper.setAccessible(true);
    return mapper.get(null);
  }
}
