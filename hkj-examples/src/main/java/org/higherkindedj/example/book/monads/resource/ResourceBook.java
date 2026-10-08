// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/monads/vtask_resource.html">Resource Management</a> page.
 * The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API, and the
 * build runs {@code main} to check the output each region claims.
 *
 * <p>The page's other blocks open connections and files, which a runnable example cannot do. They
 * keep {@code <!-- verify -->} markers instead, so they are still compiled.
 */
public final class ResourceBook {

  private ResourceBook() {}

  public static void main(String[] args) {
    // ANCHOR: many_uses
    List<String> closed = new ArrayList<>();
    AtomicInteger opened = new AtomicInteger();
    Resource<String> connection =
        Resource.make(() -> "conn-" + opened.incrementAndGet(), closed::add)
            .map(String::toUpperCase);

    List<String> both =
        connection
            .use(outer -> connection.use(inner -> VTask.succeed(List.of(outer, inner))))
            .run();
    // [CONN-1, CONN-2]
    List<String> closedInOrder = List.copyOf(closed);
    // [conn-2, conn-1]
    // ANCHOR_END: many_uses
    System.out.println(both);
    System.out.println(closedInOrder);

    // ANCHOR: on_failure
    List<String> log = new ArrayList<>();
    AtomicInteger begun = new AtomicInteger();
    Resource<String> transaction =
        Resource.make(() -> "tx-" + begun.incrementAndGet(), tx -> log.add("close " + tx))
            .onFailure(tx -> log.add("rollback " + tx));

    transaction.useSync(String::length).run();
    transaction.use(_ -> VTask.fail(new IllegalStateException("insert failed"))).runSafe();
    List<String> whatRan = List.copyOf(log);
    // [close tx-1, rollback tx-2, close tx-2]
    // ANCHOR_END: on_failure
    System.out.println(whatRan);

    // ANCHOR: finalisers
    List<String> steps = new ArrayList<>();
    Resource<String> handle =
        Resource.make(() -> "handle", _ -> steps.add("release"))
            .withFinalizer(() -> steps.add("log the release"))
            .withFinalizer(() -> steps.add("record metrics"));

    handle.useSync(String::length).run();
    List<String> ranInOrder = List.copyOf(steps);
    // [release, log the release, record metrics]
    // ANCHOR_END: finalisers
    System.out.println(ranInOrder);
  }
}
