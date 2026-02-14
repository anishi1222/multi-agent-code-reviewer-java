# Release Notes

## 2026-02-14

### Summary
- Enforced supply-chain guardrails in Maven and GitHub Actions.
- Added CI required checks and validated branch protection behavior with a real PR.
- Documented dependency and CI policies in both English and Japanese READMEs.

### Highlights
- Maven policy hardening:
  - Checksum verification for Central artifacts is enforced.
  - SNAPSHOT dependencies/plugins are blocked via Maven Enforcer.
- Dependency Review policy hardening:
  - PRs fail on vulnerabilities with severity `moderate` or higher.
  - Denied licenses: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`.
- CI enforcement:
  - Added CI workflow jobs `Supply Chain Guard` and `Build and Test`.
  - Confirmed required checks are correctly recognized by branch protection.

### Branch Protection (main)
- Required checks:
  - `Supply Chain Guard`
  - `Build and Test`
  - `dependency-review`
- Additional protections:
  - `strict` status checks enabled
  - `enforce_admins` enabled
  - `required_conversation_resolution` enabled

### Merged PRs
- [#10](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/10): enforce dependency policy and required checks

### Affected Files (PR #10)
- `.github/workflows/dependency-review.yml`
- `README_en.md`
- `README_ja.md`
