package org.higherkindedj.checker.testdata;

/**
 * Test data file: correct Path usage that should compile without errors.
 *
 * <p>All chains use the same Path type throughout.
 */
public class CorrectUsage {

  public void correctViaChain() {
    // Same-type via chaining is correct
    String result = "hello";
    result.length();
  }

  public void simpleCode() {
    // Simple code that does not use Path types at all
    int x = 1 + 2;
    String s = String.valueOf(x);
  }
}
