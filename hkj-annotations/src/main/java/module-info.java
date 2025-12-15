module org.higherkindedj.annotations {
  // Exports the general-purpose annotations package.
  exports org.higherkindedj.annotation;

  // Exports the package containing the optics code generation annotations,
  // making them available to other modules.
  exports org.higherkindedj.optics.annotations;

  // Exports the package containing the Effect Path API code generation annotations.
  exports org.higherkindedj.hkt.effect.annotation;
}
