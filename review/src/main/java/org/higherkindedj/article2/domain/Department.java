// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.util.List;
import org.higherkindedj.article2.optics.Lens;

/**
 * A department with a name, manager, and staff.
 *
 * <p>In production with higher-kinded-j, you would annotate this with {@code @GenerateLenses}.
 */
public record Department(String name, Employee manager, List<Employee> staff) {

  /** Lens accessors for Department fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Department, String> name() {
      return Lens.of(
          Department::name,
          (newName, dept) -> new Department(newName, dept.manager(), dept.staff()));
    }

    public static Lens<Department, Employee> manager() {
      return Lens.of(
          Department::manager,
          (newManager, dept) -> new Department(dept.name(), newManager, dept.staff()));
    }

    public static Lens<Department, List<Employee>> staff() {
      return Lens.of(
          Department::staff,
          (newStaff, dept) -> new Department(dept.name(), dept.manager(), newStaff));
    }
  }
}
