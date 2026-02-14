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

## 2026-02-15

### 概要
- 4レビュー（best-practices / code-quality / performance / security）の指摘 26 件（High 4 / Medium 10 / Low 12）に対応しました。
- セキュリティ、保守性、性能、設定/DI 一貫性に関する改善を実施しました。
- PR #13 を squash merge し、`main` へ反映しました。

### 主な変更
- セキュリティ強化:
  - `CustomInstructionSafetyValidator` を regex + Unicode 正規化（NFKC）ベースに強化。
  - 自動ロードされたカスタムインストラクションにも安全性検証を適用。
  - `TemplateService` のテンプレート名検証を allowlist + 正規化パス検証に変更。
  - `GithubMcpConfig` のヘッダ文字列表現で認証値をマスク化。
- コード品質/設計改善:
  - 旧重複コマンド 3 クラスを削除（デッドコード解消）。
  - `FeatureFlags` / `SkillRegistry` を Micronaut DI 管理へ統一。
  - `ReviewCommand.applyOption` を分割し複雑度を低減。
  - wildcard import を明示 import に統一。
- パフォーマンス改善:
  - `LocalFileProvider` に one-pass 収集 API（`collectAndGenerate`）を追加。
  - `ReviewContext` の共有再利用、`FindingsExtractor` の single-pass 化、`SummaryGenerator` 置換処理の軽量化。

### 検証
- `mvn -B -ntp clean package`: BUILD SUCCESS
- テスト結果: 431 run, 0 failures, 0 errors

### マージ済み PR
- [#13](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/13): fix: resolve all findings from 2026-02-15 reports

### 参照
- 詳細対応表: `reports/anishi1222/multi-agent-code-reviewer/remediation_checklist_2026-02-15.md`
