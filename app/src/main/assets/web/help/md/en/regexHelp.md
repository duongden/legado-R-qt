# Regular expressions

## Syntax
| Pattern | Meaning |
|---|---|
| `.` | One character, depending on line flags |
| `[abc]`, `[^abc]` | Character class and negated class |
| `*`, `+`, `?` | Repeat 0+, 1+, or optional |
| `{m,n}` | Bounded repetition |
| `(x)`, `x|y` | Group and alternatives |
| `^`, `$` | Start/end of string or line, depending on mode |
| `\d`, `\s`, `\w` | Digit, whitespace and word character |

## Groups and replacement
Escape special characters for literal matching. Distinguish greedy/lazy repetition (`*?`, `+?`). Lookahead/lookbehind impose conditions without necessarily consuming characters; support depends on the engine.
Backslashes often need an extra escape in JSON/JS strings. Test positive and negative cases; avoid nested repetition on long chapter text.
