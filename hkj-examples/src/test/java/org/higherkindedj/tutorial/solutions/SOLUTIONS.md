# Tutorial Solutions

This directory contains complete, working solutions for all tutorial exercises.

## Usage

The solution files mirror the structure of the tutorial files, but with all `___` placeholders replaced with working code.

**Important**: Try to solve the exercises yourself before looking at the solutions!

## Structure

- `coretypes/` - Solutions for Core Types tutorials (01-07)
- `optics/` - Solutions for Optics tutorials (01-07)

Each solution file:
- Has the same structure as the corresponding tutorial
- Contains complete, tested code for all exercises
- Includes the same documentation and explanations

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
