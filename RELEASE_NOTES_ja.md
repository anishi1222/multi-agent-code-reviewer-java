# リリースノート

## 2026-02-14

### 概要
- Maven と GitHub Actions にサプライチェーンガードレールを導入しました。
- CI の必須チェックを追加し、実PRでブランチ保護の動作を検証しました。
- 依存関係ポリシーと CI ポリシーを英語/日本語 README に明記しました。

### 主な変更
- Maven ポリシー強化:
  - Maven Central 成果物のチェックサム検証を強制。
  - Maven Enforcer により SNAPSHOT 依存関係/プラグインを禁止。
- Dependency Review ポリシー強化:
  - 脆弱性 `moderate` 以上で PR を失敗。
  - 拒否ライセンス: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`。
- CI 強制:
  - `Supply Chain Guard` と `Build and Test` ジョブを追加。
  - ブランチ保護で required checks が正しく認識されることを確認。

### ブランチ保護（main）
- Required checks:
  - `Supply Chain Guard`
  - `Build and Test`
  - `dependency-review`
- 追加保護:
  - `strict` status checks 有効
  - `enforce_admins` 有効
  - `required_conversation_resolution` 有効

### マージ済み PR
- [#10](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/10): enforce dependency policy and required checks

### 変更ファイル（PR #10）
- `.github/workflows/dependency-review.yml`
- `README_en.md`
- `README_ja.md`
