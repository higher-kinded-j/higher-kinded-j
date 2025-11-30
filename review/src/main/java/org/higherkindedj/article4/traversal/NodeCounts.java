// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.traversal;

/**
 * Counts of different node types in an expression tree.
 *
 * <p>This is a monoid: it has a zero element and an associative combine operation, making it ideal
 * for use with folds.
 *
 * @param literals count of Literal nodes
 * @param variables count of Variable nodes
 * @param binaries count of Binary nodes
 * @param conditionals count of Conditional nodes
 */
public record NodeCounts(int literals, int variables, int binaries, int conditionals) {

  /** The zero element: no nodes of any type. */
  public static final NodeCounts ZERO = new NodeCounts(0, 0, 0, 0);

  /** A single literal node. */
  public static final NodeCounts ONE_LITERAL = new NodeCounts(1, 0, 0, 0);

  /** A single variable node. */
  public static final NodeCounts ONE_VARIABLE = new NodeCounts(0, 1, 0, 0);

  /** A single binary node. */
  public static final NodeCounts ONE_BINARY = new NodeCounts(0, 0, 1, 0);

  /** A single conditional node. */
  public static final NodeCounts ONE_CONDITIONAL = new NodeCounts(0, 0, 0, 1);

  /**
   * Combine two counts by adding corresponding fields.
   *
   * @param other the other counts to add
   * @return the combined counts
   */
  public NodeCounts add(NodeCounts other) {
    return new NodeCounts(
        literals + other.literals,
        variables + other.variables,
        binaries + other.binaries,
        conditionals + other.conditionals);
  }

  /** Return the total number of nodes. */
  public int total() {
    return literals + variables + binaries + conditionals;
  }

  @Override
  public String toString() {
    return String.format(
        "NodeCounts[literals=%d, variables=%d, binaries=%d, conditionals=%d, total=%d]",
        literals, variables, binaries, conditionals, total());
  }
}
