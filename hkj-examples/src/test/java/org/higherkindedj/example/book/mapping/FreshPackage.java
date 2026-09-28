// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Runs one program's first use of a class in a class loader that defines this package afresh. A
 * fresh loader gives each order its own class initialisation, which in the shared test JVM happens
 * once, to whichever test gets there first.
 */
final class FreshPackage {

  private static final String PKG = FreshPackage.class.getPackageName() + ".";

  private FreshPackage() {}

  /**
   * Uses {@code first}, then reads the {@code MAPPER} constant declared on {@code spec}.
   *
   * @param first the simple name of the class the program uses first
   * @param spec the simple name of the spec declaring {@code MAPPER}
   * @return what the constant holds afterwards
   */
  static @Nullable Object mapperAfterFirstUsing(String first, String spec) throws Exception {
    ClassLoader fresh = freshLoader();
    Class.forName(PKG + first, true, fresh);
    Field mapper = Class.forName(PKG + spec, false, fresh).getField("MAPPER");
    mapper.setAccessible(true);
    return mapper.get(null);
  }

  /**
   * Uses {@code first} twice in one fresh loader, and answers what each use threw.
   *
   * @param first the simple name of the class the program uses
   * @return the two uses' exceptions, in order, with null for a use that threw none
   */
  static List<@Nullable Throwable> failuresUsingTwice(String first) {
    ClassLoader fresh = freshLoader();
    List<@Nullable Throwable> failures = new ArrayList<>();
    for (int use = 0; use < 2; use++) {
      try {
        Class.forName(PKG + first, true, fresh);
        failures.add(null);
      } catch (Throwable thrown) {
        failures.add(thrown);
      }
    }
    return failures;
  }

  /** A class loader that defines this package afresh and delegates everything else. */
  private static ClassLoader freshLoader() {
    ClassLoader parent = FreshPackage.class.getClassLoader();
    return new ClassLoader(parent) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith(PKG)) {
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
  }
}
