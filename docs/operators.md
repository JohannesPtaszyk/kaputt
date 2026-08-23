# Operators

| Key | Mutation |
| --- | --- |
| `conditional-boundary` | `< ↔ <=`, `> ↔ >=` |
| `negate-conditional` | `==`, `<`, `<=`, `>`, `>=` negated |
| `logical-operator` | `&&` to `\|\|` and back |
| `range-boundary` | `until` to `..` and back, on `Int` and `Long` |
| `math` | `+ ↔ -`, `* ↔ /`, `%` to `*` |
| `remove-not` | `!x` to `x`, including `x.not()` |
| `safe-call` | `a?.b` behaves like `a!!.b` |
| `empty-return` | returned value becomes `0`, `""`, an empty collection, or `false` and `true` |
| `void-call-removal` | a statement-level `Unit` call is dropped |
| `boolean-literal` | `true ↔ false` |
| `number-literal` | `n` to `n + 1` and `n - 1` |
| `string-literal` | a non-empty string becomes `""` |

## Choosing a subset

The three literal operators produce about 40% of all mutants and the lowest
share of useful findings, because they also hit every default value, log
message and constant in a data holder. The operators above `empty-return`
change control flow and carry most of the signal.

```kotlin
mutation {
    operators.addAll(
        "conditional-boundary",
        "negate-conditional",
        "math",
        "empty-return",
    )
}
```

Keep the literal operators where a string or number is behavior rather than
data: wire-format names, notification channel ids, cache keys, feature flags.
