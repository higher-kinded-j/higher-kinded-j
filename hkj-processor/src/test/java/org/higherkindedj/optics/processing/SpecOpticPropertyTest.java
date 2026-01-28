// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Property-based tests for spec-generated optics.
 *
 * <p>These tests verify that generated optics satisfy their laws across multiple random inputs,
 * providing higher confidence in the correctness of the code generation.
 */
@DisplayName("Spec Optic Property Tests")
class SpecOpticPropertyTest {

  private static final int NUM_SAMPLES = 50;
  private static final Random RANDOM = new Random(42); // Fixed seed for reproducibility

  // ==================== TEST SOURCES ====================

  private static final JavaFileObject EXTERNAL_CONFIG =
      JavaFileObjects.forSourceString(
          "com.external.Config",
          """
          package com.external;

          public final class Config {
              private final String host;
              private final int port;
              private final boolean ssl;

              public Config(String host, int port, boolean ssl) {
                  this.host = host;
                  this.port = port;
                  this.ssl = ssl;
              }

              public String host() { return host; }
              public int port() { return port; }
              public boolean ssl() { return ssl; }

              public Builder toBuilder() {
                  return new Builder().host(host).port(port).ssl(ssl);
              }

              public static class Builder {
                  private String host;
                  private int port;
                  private boolean ssl;

                  public Builder host(String host) { this.host = host; return this; }
                  public Builder port(int port) { this.port = port; return this; }
                  public Builder ssl(boolean ssl) { this.ssl = ssl; return this; }
                  public Config build() { return new Config(host, port, ssl); }
              }

              @Override
              public boolean equals(Object o) {
                  if (this == o) return true;
                  if (!(o instanceof Config c)) return false;
                  return port == c.port && ssl == c.ssl && host.equals(c.host);
              }

              @Override
              public int hashCode() {
                  return 31 * (31 * host.hashCode() + port) + (ssl ? 1 : 0);
              }
          }
          """);

  private static final JavaFileObject CONFIG_SPEC =
      JavaFileObjects.forSourceString(
          "com.test.ConfigSpec",
          """
          package com.test;

          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.ViaBuilder;
          import com.external.Config;

          @ImportOptics
          public interface ConfigSpec extends OpticsSpec<Config> {

              @ViaBuilder
              Lens<Config, String> host();

              @ViaBuilder
              Lens<Config, Integer> port();

              @ViaBuilder
              Lens<Config, Boolean> ssl();
          }
          """);

  // ==================== COMPILED RESULTS ====================

  private static PropertyTestHelper compiled;

  @BeforeAll
  static void compileTestTypes() {
    Compilation compilation =
        javac().withProcessors(new ImportOpticsProcessor()).compile(EXTERNAL_CONFIG, CONFIG_SPEC);

    assertThat(compilation.status())
        .as("Compilation should succeed")
        .isEqualTo(Compilation.Status.SUCCESS);

    compiled = new PropertyTestHelper(compilation);
  }

  // ==================== LENS PROPERTY TESTS ====================

  @Nested
  @DisplayName("Lens Property Tests")
  class LensPropertyTests {

    @TestFactory
    @DisplayName("Get-Put law holds for random inputs")
    Stream<DynamicTest> getPutLawProperty() {
      return IntStream.range(0, NUM_SAMPLES)
          .mapToObj(
              i ->
                  DynamicTest.dynamicTest(
                      "Get-Put property #" + i,
                      () -> {
                        // Generate random config
                        Object config = randomConfig();

                        // Test each lens
                        Object hostLens = compiled.invokeStatic("com.test.Config", "host");
                        Object portLens = compiled.invokeStatic("com.test.Config", "port");
                        Object sslLens = compiled.invokeStatic("com.test.Config", "ssl");

                        verifyGetPutLaw(hostLens, config);
                        verifyGetPutLaw(portLens, config);
                        verifyGetPutLaw(sslLens, config);
                      }));
    }

    @TestFactory
    @DisplayName("Put-Get law holds for random inputs")
    Stream<DynamicTest> putGetLawProperty() {
      return IntStream.range(0, NUM_SAMPLES)
          .mapToObj(
              i ->
                  DynamicTest.dynamicTest(
                      "Put-Get property #" + i,
                      () -> {
                        // Generate random config and new values
                        Object config = randomConfig();
                        String newHost = randomHost();
                        int newPort = randomPort();
                        boolean newSsl = RANDOM.nextBoolean();

                        Object hostLens = compiled.invokeStatic("com.test.Config", "host");
                        Object portLens = compiled.invokeStatic("com.test.Config", "port");
                        Object sslLens = compiled.invokeStatic("com.test.Config", "ssl");

                        verifyPutGetLaw(hostLens, config, newHost);
                        verifyPutGetLaw(portLens, config, newPort);
                        verifyPutGetLaw(sslLens, config, newSsl);
                      }));
    }

    @TestFactory
    @DisplayName("Put-Put law holds for random inputs")
    Stream<DynamicTest> putPutLawProperty() {
      return IntStream.range(0, NUM_SAMPLES)
          .mapToObj(
              i ->
                  DynamicTest.dynamicTest(
                      "Put-Put property #" + i,
                      () -> {
                        // Generate random config and two sets of values
                        Object config = randomConfig();
                        String host1 = randomHost();
                        String host2 = randomHost();
                        int port1 = randomPort();
                        int port2 = randomPort();

                        Object hostLens = compiled.invokeStatic("com.test.Config", "host");
                        Object portLens = compiled.invokeStatic("com.test.Config", "port");

                        verifyPutPutLaw(hostLens, config, host1, host2);
                        verifyPutPutLaw(portLens, config, port1, port2);
                      }));
    }

    @TestFactory
    @DisplayName("Multiple lens updates preserve independence")
    Stream<DynamicTest> lensIndependenceProperty() {
      return IntStream.range(0, NUM_SAMPLES)
          .mapToObj(
              i ->
                  DynamicTest.dynamicTest(
                      "Independence property #" + i,
                      () -> {
                        Object config = randomConfig();
                        String newHost = randomHost();
                        int newPort = randomPort();

                        Object hostLens = compiled.invokeStatic("com.test.Config", "host");
                        Object portLens = compiled.invokeStatic("com.test.Config", "port");

                        // Update host then port
                        Object updated1 =
                            compiled.invokeLensSet(
                                portLens,
                                newPort,
                                compiled.invokeLensSet(hostLens, newHost, config));

                        // Update port then host
                        Object updated2 =
                            compiled.invokeLensSet(
                                hostLens,
                                newHost,
                                compiled.invokeLensSet(portLens, newPort, config));

                        // Results should be equal (order independence)
                        assertThat(updated1).isEqualTo(updated2);

                        // Both values should be correctly set
                        assertThat(compiled.invokeLensGet(hostLens, updated1)).isEqualTo(newHost);
                        assertThat(compiled.invokeLensGet(portLens, updated1)).isEqualTo(newPort);
                      }));
    }
  }

  // ==================== HELPER METHODS ====================

  private void verifyGetPutLaw(Object lens, Object source) throws ReflectiveOperationException {
    Object currentValue = compiled.invokeLensGet(lens, source);
    Object result = compiled.invokeLensSet(lens, currentValue, source);
    assertThat(result).as("Get-Put law: set(get(s), s) == s").isEqualTo(source);
  }

  private void verifyPutGetLaw(Object lens, Object source, Object newValue)
      throws ReflectiveOperationException {
    Object updated = compiled.invokeLensSet(lens, newValue, source);
    Object retrieved = compiled.invokeLensGet(lens, updated);
    assertThat(retrieved).as("Put-Get law: get(set(a, s)) == a").isEqualTo(newValue);
  }

  private void verifyPutPutLaw(Object lens, Object source, Object value1, Object value2)
      throws ReflectiveOperationException {
    Object doubleSet =
        compiled.invokeLensSet(lens, value2, compiled.invokeLensSet(lens, value1, source));
    Object singleSet = compiled.invokeLensSet(lens, value2, source);
    assertThat(doubleSet).as("Put-Put law: set(b, set(a, s)) == set(b, s)").isEqualTo(singleSet);
  }

  private Object randomConfig() throws ReflectiveOperationException {
    return compiled.newInstance(
        "com.external.Config", randomHost(), randomPort(), RANDOM.nextBoolean());
  }

  private String randomHost() {
    String[] hosts = {
      "localhost",
      "example.com",
      "api.server.io",
      "database.internal",
      "192.168.1.1",
      "10.0.0.1",
      "service.cluster.local"
    };
    return hosts[RANDOM.nextInt(hosts.length)];
  }

  private int randomPort() {
    return 1024 + RANDOM.nextInt(64511); // Random port 1024-65535
  }

  // ==================== COMPILATION HELPER ====================

  static class PropertyTestHelper {
    private final ClassLoader classLoader;

    PropertyTestHelper(Compilation compilation) {
      this.classLoader = new PropertyTestClassLoader(compilation, getClass().getClassLoader());
    }

    public Class<?> loadClass(String className) throws ClassNotFoundException {
      return classLoader.loadClass(className);
    }

    public Object newInstance(String className, Object... args)
        throws ReflectiveOperationException {
      Class<?> clazz = loadClass(className);
      Class<?>[] argTypes = new Class<?>[args.length];
      for (int i = 0; i < args.length; i++) {
        argTypes[i] = toPrimitiveType(args[i].getClass());
      }
      return clazz.getDeclaredConstructor(argTypes).newInstance(args);
    }

    public Object invokeStatic(String className, String methodName, Object... args)
        throws ReflectiveOperationException {
      Class<?> clazz = loadClass(className);
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
          method.setAccessible(true);
          return method.invoke(null, args);
        }
      }
      throw new NoSuchMethodException(className + "." + methodName);
    }

    public Object invokeLensGet(Object lens, Object source) throws ReflectiveOperationException {
      Method getMethod = findMethod(lens.getClass(), "get", 1);
      getMethod.setAccessible(true);
      return getMethod.invoke(lens, source);
    }

    public Object invokeLensSet(Object lens, Object value, Object source)
        throws ReflectiveOperationException {
      Method setMethod = findMethod(lens.getClass(), "set", 2);
      setMethod.setAccessible(true);
      return setMethod.invoke(lens, value, source);
    }

    private Method findMethod(Class<?> clazz, String name, int paramCount)
        throws NoSuchMethodException {
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          return method;
        }
      }
      for (Method method : clazz.getMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          return method;
        }
      }
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null && superclass != Object.class) {
        return findMethod(superclass, name, paramCount);
      }
      throw new NoSuchMethodException(clazz.getName() + "." + name);
    }

    private Class<?> toPrimitiveType(Class<?> wrapper) {
      if (wrapper == Integer.class) return int.class;
      if (wrapper == Long.class) return long.class;
      if (wrapper == Double.class) return double.class;
      if (wrapper == Float.class) return float.class;
      if (wrapper == Boolean.class) return boolean.class;
      if (wrapper == Byte.class) return byte.class;
      if (wrapper == Short.class) return short.class;
      if (wrapper == Character.class) return char.class;
      return wrapper;
    }
  }

  private static class PropertyTestClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes = new HashMap<>();

    PropertyTestClassLoader(Compilation compilation, ClassLoader parent) {
      super(parent);
      for (JavaFileObject file : compilation.generatedFiles()) {
        if (file.getKind() == JavaFileObject.Kind.CLASS) {
          String className = inferClassName(file);
          try (InputStream is = file.openInputStream()) {
            classBytes.put(className, readAllBytes(is));
          } catch (IOException e) {
            throw new RuntimeException("Failed to read class file: " + file.getName(), e);
          }
        }
      }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytes = classBytes.get(name);
      if (bytes != null) {
        return defineClass(name, bytes, 0, bytes.length);
      }
      throw new ClassNotFoundException(name);
    }

    private String inferClassName(JavaFileObject file) {
      String name = file.getName();
      int classOutputIndex = name.indexOf(StandardLocation.CLASS_OUTPUT.getName());
      if (classOutputIndex >= 0) {
        name = name.substring(classOutputIndex + StandardLocation.CLASS_OUTPUT.getName().length());
      }
      if (name.startsWith("/")) {
        name = name.substring(1);
      }
      if (name.endsWith(".class")) {
        name = name.substring(0, name.length() - 6);
      }
      return name.replace('/', '.');
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[4096];
      int bytesRead;
      while ((bytesRead = is.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, bytesRead);
      }
      return buffer.toByteArray();
    }
  }
}
