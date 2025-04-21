# Contributing to Java HKT Simulation

First off, thank you for considering contributing! This project is a simulation to explore Higher-Kinded Types in Java, and contributions are welcome.

This document provides guidelines for contributing to this project.

## Code of Conduct

This project and everyone participating in it is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to magnus.smith@gmail.com.

## How Can I Contribute?

### Reporting Bugs

* Ensure the bug was not already reported by searching on GitHub under [Issues](https://github.com/MagnusSmith/simulation-hkt/issues).
* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/MagnusSmith/simulation-hkt/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample or an executable test case** demonstrating the expected behavior that is not occurring.
* Use the "Bug Report" issue template if available.

### Suggesting Enhancements

* Open a new issue to discuss your enhancement suggestion. Please provide details about the motivation and potential implementation.
* Use the "Feature Request" issue template if available.

### Your First Code Contribution

Unsure where to begin contributing? You can start by looking through `good first issue` or `help wanted` issues (you can add these labels yourself to issues you think fit).

### Pull Requests

1.  **Fork the repository** on GitHub.
2.  **Clone your fork** locally: `git clone git@github.com:MagnusSmith/simulation-hkt.git`
3.  **Create a new branch** for your changes: `git checkout -b name-of-your-feature-or-fix`
4.  **Make your changes.** Ensure you adhere to standard Java coding conventions.
5.  **Add tests** for your changes. This is important!
6.  **Run the tests:** Make sure the full test suite passes using `./gradlew test`.
7.  **Build the project:** Ensure the project builds without errors using `./gradlew build`.
8.  **Commit your changes:** Use clear and descriptive commit messages. `git commit -am 'Add some feature'`
9.  **Push to your fork:** `git push origin name-of-your-feature-or-fix`
10. **Open a Pull Request** against the `main` branch of the original repository.
11. **Describe your changes** in the Pull Request description. Link to any relevant issues (e.g., "Closes #123").
12. Ensure the **GitHub Actions CI checks pass**.

## Development Setup

* You need a Java Development Kit (JDK), version **24** or later.
* This project uses Gradle. You can use the included Gradle Wrapper (`gradlew`) to build and test.
    * Build the project: `./gradlew build`
    * Run tests: `./gradlew test`
    * Generate JaCoCo coverage reports: `./gradlew test jacocoTestReport` (HTML report at `build/reports/jacoco/test/html/index.html`)

## Coding Style

Please follow standard Java coding conventions. Keep code simple, readable, and well-tested.

Thank you for contributing!
