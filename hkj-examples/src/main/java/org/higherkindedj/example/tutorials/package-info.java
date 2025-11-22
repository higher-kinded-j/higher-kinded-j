// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Complete, polished examples that complement the interactive tutorial series.
 *
 * <p>These examples demonstrate the patterns and techniques taught in the tutorials, but in a
 * production-ready format. They're designed to:
 *
 * <ul>
 *   <li><b>Show Best Practices:</b> Demonstrate idiomatic usage of Higher-Kinded-J patterns
 *   <li><b>Provide Context:</b> Place tutorial concepts into realistic scenarios
 *   <li><b>Serve as Templates:</b> Offer starting points for your own implementations
 *   <li><b>Extend Learning:</b> Go beyond the basics with advanced variations
 * </ul>
 *
 * <h2>Relationship to Tutorials</h2>
 *
 * <table>
 *   <caption>Tutorial Examples Mapping</caption>
 *   <tr><th>Example</th><th>Related Tutorials</th><th>Concepts Demonstrated</th></tr>
 *   <tr>
 *     <td>{@link TutorialGettingStarted}</td>
 *     <td>Setup verification</td>
 *     <td>Dependency configuration, basic type usage</td>
 *   </tr>
 *   <tr>
 *     <td>{@link FunctorTransformations}</td>
 *     <td>Core Types Tutorial 02</td>
 *     <td>Functor patterns, generic transformations</td>
 *   </tr>
 *   <tr>
 *     <td>{@link ApplicativeValidation}</td>
 *     <td>Core Types Tutorials 03, 06</td>
 *     <td>Form validation, error accumulation</td>
 *   </tr>
 *   <tr>
 *     <td>{@link MonadicWorkflow}</td>
 *     <td>Core Types Tutorials 04, 05, 07</td>
 *     <td>Chaining operations, error handling</td>
 *   </tr>
 *   <tr>
 *     <td>{@link LensDeepUpdate}</td>
 *     <td>Optics Tutorials 01, 02</td>
 *     <td>Lens composition, nested updates</td>
 *   </tr>
 *   <tr>
 *     <td>{@link TraversalBulkOps}</td>
 *     <td>Optics Tutorials 04, 07</td>
 *     <td>Collection operations, bulk transformations</td>
 *   </tr>
 *   <tr>
 *     <td>{@link OpticComposition}</td>
 *     <td>Optics Tutorials 05, 08, 09</td>
 *     <td>Complex composition, Free Monad DSL</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Notes</h2>
 *
 * <p>These examples are <b>runnable</b> (they contain {@code main} methods) and <b>executable as
 * tests</b> where appropriate. They use:
 *
 * <ul>
 *   <li><b>British English</b> in all prose (behaviour, organisation, whilst)
 *   <li><b>Extensive Javadoc</b> explaining every pattern and decision
 *   <li><b>Real-world scenarios</b> that mirror production challenges
 *   <li><b>Commented variations</b> showing alternative approaches
 * </ul>
 *
 * <h2>Learning Path</h2>
 *
 * <ol>
 *   <li>Complete the relevant tutorial exercises first
 *   <li>Run these examples to see the patterns in context
 *   <li>Study the Javadoc to understand design decisions
 *   <li>Experiment with the commented variations
 *   <li>Apply the patterns to your own projects
 * </ol>
 *
 * @see <a href="https://higher-kinded-j.github.io/tutorials/tutorials_intro.html">Interactive
 *     Tutorials</a>
 * @see <a href="https://higher-kinded-j.github.io/tutorials/coretypes_track.html">Core Types
 *     Track</a>
 * @see <a href="https://higher-kinded-j.github.io/tutorials/optics_track.html">Optics Track</a>
 */
package org.higherkindedj.example.tutorials;
