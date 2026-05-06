# Tutorial Solutions

This directory contains complete, working solutions for every tutorial exercise.

## Usage

The solution files mirror the structure of the tutorial files, but with all `answerRequired()` placeholders replaced with working code, plus extra commentary on *why* the solution is idiomatic.

**Important**: Try to solve the exercises yourself before looking at the solutions! See [`solutions_guide.md`](../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md) in the book for guidance on how to learn from a solution rather than copy it.

## Structure

| Directory | Solutions | Matches tutorial directory |
|-----------|-----------|----------------------------|
| `coretypes/` | 11 | `tutorial/coretypes/` |
| `effect/` | 2 | `tutorial/effect/` |
| `transformers/` | 4 | `tutorial/transformers/` |
| `concurrency/` | 8 | `tutorial/concurrency/` |
| `optics/` | 20 | `tutorial/optics/` |
| `expression/` | 4 | `tutorial/expression/` |
| `context/` | 6 | `tutorial/context/` |
| `effecthandlers/` | 6 | `tutorial/effecthandlers/` |
| `resilience/` | 4 | `tutorial/resilience/` |

Each solution file:
- Has the same structure as the corresponding tutorial
- Contains complete, tested code for all exercises
- Includes commentary on the idiomatic choice, alternatives, and common wrong attempts (rolling out across journeys; see the foundations pilot for the format)

## How to Use Solutions

1. **First, try the exercise yourself** - Give it your best shot!
2. **If stuck, look at the hint** - Tutorials include hints for each exercise
3. **Still stuck? Check the solution** - Compare your approach with the working code
4. **Understand the solution** - Don't just copy - understand WHY it works
5. **Go back and complete it yourself** - Delete the copied code and try again

## Running Solutions

Solutions are just regular tests. You can run them to verify they work:

```bash
./gradlew :hkj-examples:test --tests "*solutions*"
```

## Learning Tips

- Compare your solution with the provided solution
- Look for alternative approaches
- Understand the patterns and idioms used
- Ask yourself: "Could this be done differently?"

Happy learning!
