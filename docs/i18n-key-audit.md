# i18n Key Audit

Date: 2026-03-13

## Scope

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `core/ui/src/main/res/values/strings.xml`
- `core/ui/src/main/res/values-zh/strings.xml`

## Result Summary

| Module | en keys | zh keys | Missing in zh | Missing in en |
|---|---:|---:|---:|---:|
| app | 144 | 144 | 0 | 0 |
| core/ui | 17 | 17 | 0 | 0 |

## Conclusion

- No missing localization keys detected between English and Chinese resources.
- Current localization key parity is healthy.

## Recommended Follow-up

1. Re-run this audit whenever adding new strings, especially for feature toggles and error states.
2. Add this check into CI as a lightweight script gate in a future iteration.
