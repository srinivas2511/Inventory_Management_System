# test/controlling/ CLAUDE.md

Controlling and product costing tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `ControllingIT` | IT | 13 | Cost-centre and profit-centre CRUD; journal dimension allocation; period-end cost report accuracy; budget import (CSV) and actual vs budget variance |
| `ControllingSecurityMatrixTest` | ArchUnit | — | Every controlling endpoint has `CONTROLLING_VIEW` / `CONTROLLING_MANAGE` |
| `ConversionCostCalculatorTest` | Unit | — | `controlling/service/` — pure calculation: conversion cost absorbed = (actual labour + overhead) × allocation rate; no Spring context |

## Notes
- `ControllingIT` is also the guard for the `ConversionAbsorptionIT` accounting side: after a production order completes, `ControllingIT` asserts that the absorbed cost appears in the cost-centre actual vs budget comparison.
- `ConversionCostCalculatorTest` uses parameterised inputs to cover rounding edge cases (Indian rupee, 4 decimal places for money).
