# Multi-Agent Code Reviewer

GitHub Copilot SDK for Java を使用した、複数のAIエージェントによる並列コードレビューアプリケーションです。

## 特徴

- **複数エージェント並列実行**: セキュリティ、コード品質、パフォーマンス、ベストプラクティスの各観点から同時レビュー
- **柔軟なエージェント定義**: YAML形式 (.yaml) または GitHub Copilot形式 (.agent.md) でエージェントを定義
- **外部設定ファイル**: エージェント定義はビルド不要で差し替え可能
- **LLMモデル指定**: レビュー、レポート生成、サマリー生成で異なるモデルを使用可能
- **構造化されたレビュー結果**: Priority（Critical/High/Medium/Low）付きの一貫したフォーマット
- **エグゼクティブサマリー生成**: 全レビュー結果を集約した経営層向けレポート
- **GraalVM対応**: Native Image によるネイティブバイナリの生成が可能

## 要件

- **GraalVM 25.0.2** (Java 21)
- GitHub Copilot CLI 0.0.401 以上
- GitHub トークン（リポジトリアクセス用）

### GraalVM のインストール

SDKMAN を使用する場合:

```bash
sdk install java 25.0.2-graal
sdk use java 25.0.2-graal

# プロジェクトディレクトリで自動切り替え
cd multi-agent-reviewer  # .sdkmanrc により自動的にGraalVMが選択される
```

## インストール

```bash
# リポジトリをクローン
git clone https://github.com/your-org/multi-agent-reviewer.git
cd multi-agent-reviewer

# ビルド（JARファイル）
mvn clean package

# ネイティブイメージをビルド（オプション）
mvn clean package -Pnative
```

## 使い方

### 基本的な使用方法

```bash
# 全エージェントでレビュー実行
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  --repo owner/repository \
  --all

# 特定のエージェントのみ実行
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  --repo owner/repository \
  --agents security,performance

# LLMモデルを明示的に指定
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  --repo owner/repository \
  --all \
  --review-model gpt-4.1 \
  --summary-model claude-sonnet-4
```

### オプション一覧

| オプション | 短縮形 | 説明 | デフォルト |
|-----------|--------|------|-----------|
| `--repo` | `-r` | 対象リポジトリ（必須） | - |
| `--agents` | `-a` | 実行するエージェント（カンマ区切り） | - |
| `--all` | - | 全エージェント実行 | false |
| `--output` | `-o` | 出力ディレクトリ | `./report` |
| `--agents-dir` | - | 追加のエージェント定義ディレクトリ | - |
| `--token` | - | GitHub トークン | `$GITHUB_TOKEN` |
| `--parallelism` | - | 並列実行数 | 4 |
| `--list` | - | 利用可能なエージェント一覧 | - |
| `--no-summary` | - | サマリー生成をスキップ | false |
| `--model` | - | 全ステージのデフォルトモデル | - |
| `--review-model` | - | レビュー用モデル | エージェント設定 |
| `--report-model` | - | レポート生成用モデル | review-model |
| `--summary-model` | - | サマリー生成用モデル | claude-sonnet-4 |
| `--help` | `-h` | ヘルプ表示 | - |
| `--version` | `-V` | バージョン表示 | - |

### 環境変数

```bash
export GITHUB_TOKEN=your_github_token
```

### 出力例

```
./report/
├── security_260204.md
├── code-quality_260204.md
├── performance_260204.md
├── best-practices_260204.md
└── executive_summary_260204.md
```

## エージェント定義

### 対応フォーマット

エージェントは2つの形式で定義できます:

1. **YAML形式** (`.yaml`, `.yml`) - 従来の形式
2. **GitHub Copilot形式** (`.agent.md`) - Markdownベースの形式

### エージェントディレクトリ

以下のディレクトリが自動的に検索されます:

- `./agents/` - デフォルトディレクトリ
- `./.github/agents/` - GitHub Copilot形式のディレクトリ

`--agents-dir` オプションで追加のディレクトリを指定できます。

### YAML形式 (`agents/security.yaml`)

```yaml
name: security
displayName: "セキュリティレビュー"
model: claude-sonnet-4
systemPrompt: |
  あなたはセキュリティ専門のコードレビュアーです。
  以下の観点でコードを分析してください：
focusAreas:
  - SQLインジェクション
  - XSS脆弱性
  - 認証・認可の問題
```

### GitHub Copilot形式 (`.github/agents/security.agent.md`)

```markdown
---
name: security
description: "セキュリティレビュー"
model: claude-sonnet-4
---

# セキュリティレビューエージェント

あなたはセキュリティ専門のコードレビュアーです。
豊富な経験を持つセキュリティエンジニアとして、コードの脆弱性を特定します。

## Focus Areas

- SQLインジェクション
- XSS脆弱性
- 認証・認可の問題
```

### デフォルトエージェント

| エージェント | 説明 |
|-------------|------|
| `security` | セキュリティ脆弱性、認証・認可、機密情報 |
| `code-quality` | 可読性、複雑度、SOLID原則、テスト |
| `performance` | N+1クエリ、メモリリーク、アルゴリズム効率 |
| `best-practices` | 言語・フレームワーク固有のベストプラクティス |

## レビュー結果フォーマット

各指摘事項は以下の形式で出力されます：

| 項目 | 説明 |
|------|------|
| タイトル | 問題を簡潔に表すタイトル |
| Priority | Critical / High / Medium / Low |
| 指摘の概要 | 何が問題かの説明 |
| 修正しない場合の影響 | 放置した場合のリスク |
| 該当箇所 | ファイルパスと行番号 |
| 推奨対応 | 具体的な修正方法（コード例含む） |
| 効果 | 修正による改善効果 |

### Priority の基準

- **Critical**: セキュリティ脆弱性、データ損失、本番障害。即時対応必須
- **High**: 重大なバグ、パフォーマンス問題。早急な対応が必要
- **Medium**: コード品質の問題、保守性の低下。計画的に対応
- **Low**: スタイルの問題、軽微な改善提案。時間があれば対応

## GraalVM Native Image

ネイティブバイナリとしてビルドする場合:

```bash
# ネイティブイメージをビルド
mvn clean package -Pnative

# 実行
./target/review --repo owner/repository --all
```

## プロジェクト構造

```
multi-agent-reviewer/
├── pom.xml                              # Maven設定
├── .sdkmanrc                            # SDKMAN GraalVM設定
├── agents/                              # YAML形式のエージェント定義
│   ├── security.yaml
│   ├── code-quality.yaml
│   ├── performance.yaml
│   └── best-practices.yaml
├── .github/agents/                      # GitHub Copilot形式のエージェント定義
│   ├── security.agent.md
│   ├── code-quality.agent.md
│   ├── performance.agent.md
│   └── best-practices.agent.md
└── src/main/java/com/example/reviewer/
    ├── ReviewApp.java                   # CLIエントリポイント
    ├── agent/
    │   ├── AgentConfig.java             # 設定モデル
    │   ├── AgentConfigLoader.java       # 設定読込
    │   └── AgentMarkdownParser.java     # .agent.md パーサー
    ├── config/
    │   └── ModelConfig.java             # LLMモデル設定
    ├── orchestrator/
    │   └── ReviewOrchestrator.java      # 並列実行制御
    └── report/
        ├── ReviewResult.java            # 結果モデル
        ├── ReportGenerator.java         # 個別レポート生成
        └── SummaryGenerator.java        # サマリー生成
```

## ライセンス

MIT License
