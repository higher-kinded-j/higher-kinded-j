/** Compile-time checker for Higher-Kinded-J Path type mismatches. */
module org.higherkindedj.checker {
  requires jdk.compiler;

  provides com.sun.source.util.Plugin with
      org.higherkindedj.checker.HKJCheckerPlugin;
}
