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
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Helper for compiling test sources at runtime and loading generated classes.
 *
 * <p>This enables testing that generated optics satisfy their mathematical laws by:
 *
 * <ol>
 *   <li>Compiling test types with the annotation processor
 *   <li>Loading generated classes via a custom ClassLoader
 *   <li>Creating instances and invoking methods via reflection
 * </ol>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * var result = RuntimeCompilationHelper.compile(
 *     JavaFileObjects.forSourceString("com.test.Person", "..."),
 *     packageInfo
 * );
 *
 * // Get a lens and verify laws
 * Object nameLens = result.invokeStatic("com.test.PersonLenses", "name");
 * Object person = result.newInstance("com.test.Person", "Alice", 30);
 *
 * // Use reflection to call lens.get(person)
 * Object value = result.invokeLensGet(nameLens, person);
 * }</pre>
 */
public class RuntimeCompilationHelper {

  /**
   * Compiles the given source files with the ImportOpticsProcessor.
   *
   * @param sources the source files to compile
   * @return a CompiledResult providing access to generated classes
   */
  public static CompiledResult compile(JavaFileObject... sources) {
    Compilation compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(sources);

    assertThat(compilation.status())
        .as("Compilation should succeed")
        .isEqualTo(Compilation.Status.SUCCESS);

    return new CompiledResult(compilation);
  }

  /**
   * Creates a package-info.java source that imports the given types.
   *
   * @param packageName the package for the generated optics
   * @param typeNames fully qualified names of types to import
   * @return a JavaFileObject for the package-info
   */
  public static JavaFileObject packageInfo(String packageName, String... typeNames) {
    StringBuilder imports = new StringBuilder();
    StringBuilder classes = new StringBuilder();

    for (int i = 0; i < typeNames.length; i++) {
      String typeName = typeNames[i];
      imports.append("import ").append(typeName).append(";\n");
      if (i > 0) classes.append(", ");
      classes.append(typeName.substring(typeName.lastIndexOf('.') + 1)).append(".class");
    }

    String source =
        String.format(
            """
            @ImportOptics({%s})
            package %s;

            import org.higherkindedj.optics.annotations.ImportOptics;
            %s
            """,
            classes, packageName, imports);

    return JavaFileObjects.forSourceString(packageName + ".package-info", source);
  }

  /** Result of a successful compilation, providing access to generated classes. */
  public static class CompiledResult {
    private final Compilation compilation;
    private final ClassLoader classLoader;

    CompiledResult(Compilation compilation) {
      this.compilation = compilation;
      this.classLoader = new CompiledClassLoader(compilation, getClass().getClassLoader());
    }

    /** Returns the underlying compilation for additional assertions. */
    public Compilation compilation() {
      return compilation;
    }

    /**
     * Loads a class by name from the compiled output.
     *
     * @param className fully qualified class name
     * @return the loaded Class
     * @throws ClassNotFoundException if the class cannot be found
     */
    public Class<?> loadClass(String className) throws ClassNotFoundException {
      return classLoader.loadClass(className);
    }

    /**
     * Creates a new instance of a record type.
     *
     * @param className fully qualified class name
     * @param args constructor arguments
     * @return the new instance
     */
    public Object newInstance(String className, Object... args)
        throws ReflectiveOperationException {
      Class<?> clazz = loadClass(className);
      Class<?>[] argTypes = new Class<?>[args.length];
      for (int i = 0; i < args.length; i++) {
        argTypes[i] = toPrimitiveType(args[i].getClass());
      }
      return clazz.getDeclaredConstructor(argTypes).newInstance(args);
    }

    /**
     * Invokes a static method (typically a lens/prism factory method).
     *
     * @param className fully qualified class name
     * @param methodName the method name
     * @param args method arguments
     * @return the method result
     */
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

    /**
     * Invokes a static method with explicit parameter types (e.g., for enum valueOf).
     *
     * @param className fully qualified class name
     * @param methodName the method name
     * @param paramType the parameter type
     * @param arg the method argument
     * @return the method result
     */
    public Object invokeStatic(String className, String methodName, Class<?> paramType, Object arg)
        throws ReflectiveOperationException {
      Class<?> clazz = loadClass(className);
      Method method = clazz.getMethod(methodName, paramType);
      method.setAccessible(true);
      return method.invoke(null, arg);
    }

    /**
     * Invokes lens.get(source) on a Lens object.
     *
     * @param lens the Lens object
     * @param source the source object
     * @return the focused value
     */
    public Object invokeLensGet(Object lens, Object source) throws ReflectiveOperationException {
      Method getMethod = findMethod(lens.getClass(), "get", 1);
      getMethod.setAccessible(true);
      return getMethod.invoke(lens, source);
    }

    /**
     * Invokes lens.set(value, source) on a Lens object.
     *
     * @param lens the Lens object
     * @param value the new value
     * @param source the source object
     * @return the updated source
     */
    public Object invokeLensSet(Object lens, Object value, Object source)
        throws ReflectiveOperationException {
      Method setMethod = findMethod(lens.getClass(), "set", 2);
      setMethod.setAccessible(true);
      return setMethod.invoke(lens, value, source);
    }

    /**
     * Invokes prism.getOptional(source) on a Prism object.
     *
     * @param prism the Prism object
     * @param source the source object
     * @return Optional containing the focused value if it matches
     */
    @SuppressWarnings("unchecked")
    public Optional<Object> invokePrismGetOptional(Object prism, Object source)
        throws ReflectiveOperationException {
      Method getOptionalMethod = findMethod(prism.getClass(), "getOptional", 1);
      getOptionalMethod.setAccessible(true);
      return (Optional<Object>) getOptionalMethod.invoke(prism, source);
    }

    /**
     * Invokes prism.build(value) on a Prism object.
     *
     * @param prism the Prism object
     * @param value the value to build from
     * @return the built source value
     */
    public Object invokePrismBuild(Object prism, Object value) throws ReflectiveOperationException {
      Method buildMethod = findMethod(prism.getClass(), "build", 1);
      buildMethod.setAccessible(true);
      return buildMethod.invoke(prism, value);
    }

    /** Finds a method by name and parameter count, searching the class hierarchy. */
    private Method findMethod(Class<?> clazz, String name, int paramCount)
        throws NoSuchMethodException {
      // Search declared methods first (includes private methods)
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          return method;
        }
      }
      // Search all methods (includes inherited public methods)
      for (Method method : clazz.getMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          return method;
        }
      }
      // Search superclass
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

  /** ClassLoader that loads classes from compilation output. */
  private static class CompiledClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes = new HashMap<>();

    CompiledClassLoader(Compilation compilation, ClassLoader parent) {
      super(parent);
      // Load all generated class files
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
      // Convert file path to class name
      // e.g., /CLASS_OUTPUT/com/test/PersonLenses.class -> com.test.PersonLenses
      String name = file.getName();
      // Remove the location prefix and .class suffix
      int classOutputIndex = name.indexOf(StandardLocation.CLASS_OUTPUT.getName());
      if (classOutputIndex >= 0) {
        name = name.substring(classOutputIndex + StandardLocation.CLASS_OUTPUT.getName().length());
      }
      // Handle path separators
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
