# Multi-Agent Code Reviewer

GitHub Copilot SDK for Java を使用した、複数のAIエージェントによる並列コードレビューアプリケーションです。

![alt text](image.png)

## 特徴

- **複数エージェント並列実行**: セキュリティ、コード品質、パフォーマンス、ベストプラクティスの各観点から同時レビュー
- **GitHubリポジトリ/ローカルディレクトリ対応**: GitHubリポジトリまたはローカルディレクトリのソースコードをレビュー
- **カスタムインストラクション**: プロジェクト固有のルールやガイドラインをレビューに反映
- **柔軟なエージェント定義**: GitHub Copilot形式 (.agent.md) でエージェントを定義
- **エージェントスキル対応**: エージェントに個別のスキルを定義し、特定のタスクを実行可能
- **外部設定ファイル**: エージェント定義はビルド不要で差し替え可能
- **LLMモデル指定**: レビュー、レポート生成、サマリー生成で異なるモデルを使用可能
- **構造化されたレビュー結果**: Priority（Critical/High/Medium/Low）付きの一貫したフォーマット
- **エグゼクティブサマリー生成**: 全レビュー結果を集約した経営層向けレポート
- **GraalVM対応**: Native Image によるネイティブバイナリの生成が可能
- **推論モデル対応**: Claude Opus、o3、o4-mini等の推論モデルに対するreasoning effortの自動設定
- **コンテンツサニタイズ**: LLM出力からの不要な前置き文・思考過程の自動除去
- **デフォルトモデルの外部化**: `application.yml` でデフォルトモデルを設定可能（ビルド不要で変更可能）

## 要件

- **GraalVM 25.0.2** (Java 25)
- GitHub Copilot CLI 0.0.407 以上
- GitHub トークン（リポジトリアクセス用）

### GraalVM の インストール

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

> 注意: 本プロジェクトは Java のプレビュー機能を利用しています。JVM で実行する場合は `--enable-preview` を付けてください。

### 基本的な使用方法

```bash
# 全エージェントでレビュー実行（GitHubリポジトリ）
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --all

# ローカルディレクトリのレビュー
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all

# 特定のエージェントのみ実行
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --agents security,performance

# LLMモデルを明示的に指定
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --all \
  --review-model gpt-4.1 \
  --summary-model claude-sonnet-4

# カスタムインストラクションを指定してレビュー
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all \
  --instructions ./my-instructions.md

# 利用可能なエージェント一覧
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  list
```

### run オプション一覧

| オプション | 短縮形 | 説明 | デフォルト |
|-----------|--------|------|-----------|
| `--repo` | `-r` | 対象GitHubリポジトリ（`--local`と排他） | - |
| `--local` | `-l` | 対象ローカルディレクトリ（`--repo`と排他） | - |
| `--agents` | `-a` | 実行するエージェント（カンマ区切り） | - |
| `--all` | - | 全エージェント実行 | false |
| `--output` | `-o` | 出力ディレクトリ | `./report` |
| `--agents-dir` | - | 追加のエージェント定義ディレクトリ | - |
| `--token` | - | GitHub トークン | `$GITHUB_TOKEN` |
| `--parallelism` | - | 並列実行数 | 4 |
| `--no-summary` | - | サマリー生成をスキップ | false |
| `--model` | - | 全ステージのデフォルトモデル | - |
| `--review-model` | - | レビュー用モデル | エージェント設定 |
| `--report-model` | - | レポート生成用モデル | review-model |
| `--summary-model` | - | サマリー生成用モデル | default-model |
| `--instructions` | - | カスタムインストラクションファイル（複数指定可） | - |
| `--no-instructions` | - | カスタムインストラクションの自動読込を無効化 | false |
| `--help` | `-h` | ヘルプ表示 | - |
| `--version` | `-V` | バージョン表示 | - |
| `--verbose` | `-v` | 詳細ログ出力（debugレベル） | - |

### list サブコマンド

利用可能なエージェント一覧を表示します。`--agents-dir` で追加のディレクトリも指定可能です。

### 環境変数

```bash
export GITHUB_TOKEN=your_github_token
```

### 機能フラグ（Structured Concurrency）

構造化並列を有効化するには、環境変数またはJVMプロパティを使用します。

```bash
# 全体を対象に構造化並列を有効化
export REVIEWER_STRUCTURED_CONCURRENCY=true

# スキル実行のみ構造化並列を有効化
export REVIEWER_STRUCTURED_CONCURRENCY_SKILLS=true

# JVMプロパティ（代替）
java -Dreviewer.structuredConcurrency=true -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
java -Dreviewer.structuredConcurrency.skills=true -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar skill --list
```

### ローカルディレクトリレビュー

GitHubリポジトリにアクセスできない環境でも、ローカルディレクトリのソースコードをレビューできます。

```bash
# ローカルプロジェクトをレビュー
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local /path/to/project \
  --all
```

対応しているファイル拡張子:
- Java: `.java`
- Kotlin: `.kt`, `.kts`
- JavaScript/TypeScript: `.js`, `.ts`, `.jsx`, `.tsx`
- Python: `.py`
- Go: `.go`
- Ruby: `.rb`
- その他: `.c`, `.cpp`, `.h`, `.cs`, `.rs`, `.swift`, `.php`

### カスタムインストラクション

プロジェクト固有のルールやガイドラインをレビューに反映できます。

```bash
# インストラクションファイルを指定
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all \
  --instructions ./coding-standards.md \
  --instructions ./security-guidelines.md

# 自動読込を無効化
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all \
  --no-instructions
```

#### 自動検出されるインストラクションファイル

ローカルディレクトリレビュー時、以下のパスからカスタムインストラクションが自動的に読み込まれます（優先度順）:

1. `.github/copilot-instructions.md`
2. `.github/instructions/*.instructions.md` （GitHub Copilot スコープ付きインストラクション）
3. `.copilot/instructions.md`
4. `INSTRUCTIONS.md`
5. `.instructions.md`
6. `copilot-instructions.md`

#### GitHub Copilot スコープ付きインストラクション

GitHub Copilot の `.github/instructions/*.instructions.md` 形式をサポートしています。  
YAML フロントマターで `applyTo`（対象ファイルのglobパターン）と `description`（説明）を指定できます。

```markdown
---
applyTo: '**/*.java'
description: 'Java コーディング規約'
---
- クラス名はUpperCamelCaseを使用すること
- publicメソッドにはJavadocを記述すること
- 可能な限りイミュータブルなオブジェクトを使用すること
```

複数のインストラクションファイルを `.github/instructions/` ディレクトリに配置できます：

```
.github/
├── copilot-instructions.md          # リポジトリ全体のインストラクション
└── instructions/
    ├── java.instructions.md         # Java固有のルール
    ├── typescript.instructions.md   # TypeScript固有のルール
    └── security.instructions.md     # セキュリティガイドライン
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

## 設定ファイル

`application.yml` でアプリケーションの動作をカスタマイズできます。

```yaml
reviewer:
  execution:
    parallelism: 4              # デフォルトの並列実行数
    orchestrator-timeout-minutes: 45  # オーケストレータタイムアウト（分）
    agent-timeout-minutes: 20   # エージェントタイムアウト（分）
    idle-timeout-minutes: 5     # アイドルタイムアウト（分）— イベントなしで自動終了
    skill-timeout-minutes: 20   # スキルタイムアウト（分）
    summary-timeout-minutes: 20 # サマリータイムアウト（分）
    gh-auth-timeout-seconds: 30 # GitHub認証タイムアウト（秒）
    max-retries: 2              # レビュー失敗時の最大リトライ回数
  templates:
    directory: templates              # テンプレートディレクトリ
    output-constraints: output-constraints.md  # 出力制約テンプレート
  skills:
    filename: SKILL.md                    # スキル定義ファイル名
    directory: .github/skills             # スキル定義ディレクトリ
  mcp:
    github:
      type: http
      url: https://api.githubcopilot.com/mcp/
      tools:
        - "*"
      auth-header-name: Authorization
      auth-header-template: "Bearer {token}"
  models:
    default-model: claude-sonnet-4.5  # 全モデルのデフォルト（ビルド不要で変更可能）
    review-model: GPT-5.2-Codex      # レビュー用モデル
    report-model: claude-opus-4.6    # レポート生成用モデル
    summary-model: claude-sonnet-4.5 # サマリー生成用モデル
    reasoning-effort: high           # 推論モデルのエフォートレベル (low/medium/high)
```

### モデル設定の優先順位

モデルは以下の優先順位で決定されます：

1. **個別モデル設定**（`review-model`, `report-model`, `summary-model`）が最優先
2. **デフォルトモデル**（`default-model`）— 個別設定がない場合のフォールバック
3. **ハードコード定数**（`ModelConfig.DEFAULT_MODEL`）— YAMLにも定義がない場合の最終フォールバック

### リトライ機能

エージェントのレビューがタイムアウトや空レスポンスで失敗した場合、自動的にリトライします。

- **タイムアウトは各試行ごとに独立**: `agent-timeout-minutes` は累積ではなく、各試行に対して個別に適用されます。例えば `agent-timeout-minutes: 20` かつ `max-retries: 2` の場合、最大 3 回（初回 + 2 回リトライ）× 各 20 分 = 最大 60 分まで試行します
- **成功時は即座に返却**: いずれかの試行で成功すれば、残りのリトライはスキップされます
- **`max-retries: 0`** でリトライを無効化できます
- リトライ対象: タイムアウト（`TimeoutException`）、空レスポンス、SDK例外

### エージェントディレクトリ

以下のディレクトリが自動的に検索されます:

- `./agents/` - デフォルトディレクトリ
- `./.github/agents/` - 代替ディレクトリ

`--agents-dir` オプションで追加のディレクトリを指定できます。

### エージェント定義ファイル (`.agent.md`)

GitHub Copilot Custom Agent の書式に従い、セクション名はすべて英語で記述します。認識されるセクション:

| セクション | 説明 |
|---------|------|
| `## Role` | エージェントの役割・システムプロンプト |
| `## Instruction` | レビュー依頼プロンプト |
| `## Focus Areas` | レビュー観点のリスト |
| `## Output Format` | 出力フォーマット |

`Instruction` では `${repository}`, `${displayName}`, `${focusAreas}` のプレースホルダーが利用できます。

```markdown
---
name: security
description: "セキュリティレビュー"
model: claude-sonnet-4
---

# セキュリティレビューエージェント

## Role

あなたはセキュリティ専門のコードレビュアーです。
豊富な経験を持つセキュリティエンジニアとして、コードの脆弱性を特定します。

## Instruction

以下のGitHubリポジトリのコードレビューを実施してください。

**対象リポジトリ**: ${repository}

リポジトリ内のすべてのソースコードを分析し、あなたの専門分野（${displayName}）の観点から問題点を特定してください。

特に以下の点に注目してください：
${focusAreas}

## Focus Areas

- SQLインジェクション
- XSS脆弱性
- 認証・認可の問題

## Output Format

レビュー結果は必ず以下の形式で出力してください。
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

## Agent Skill

エージェントには個別のスキルを定義し、特定のタスクを実行できます。

### skill サブコマンド

```bash
# 利用可能なスキル一覧
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  skill --list

# スキルを実行
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  skill sql-injection-check \
  --param target=owner/repository

# パラメータ付きでスキル実行
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  skill secret-scan \
  --param repository=owner/repository \
  --model claude-sonnet-4
```

### skill オプション一覧

| オプション | 短縮形 | 説明 | デフォルト |
|-----------|--------|------|-----------|
| `--list` | - | 利用可能なスキル一覧を表示 | - |
| `--param` | `-p` | パラメータ（key=value形式） | - |
| `--token` | - | GitHub トークン | `$GITHUB_TOKEN` |
| `--model` | - | 使用するLLMモデル | default-model |
| `--agents-dir` | - | エージェント定義ディレクトリ | - |

### スキル定義（`SKILL.md` 形式）

スキルは `.github/skills/<スキル名>/` ディレクトリに配置された個別の `SKILL.md` ファイルとして定義します。各スキルは YAML フロントマターとマークダウン本文で構成されます。

```
.github/skills/
├── sql-injection-check/
│   └── SKILL.md
├── secret-scan/
│   └── SKILL.md
├── complexity-analysis/
│   └── SKILL.md
└── ...
```

#### SKILL.md の書式

```markdown
---
name: secret-scan
description: コード内のハードコードされた機密情報を検出します。APIキー、トークン、パスワード、秘密鍵、クラウド認証情報を対象にします。
metadata:
  agent: security
---

# 機密情報スキャン

以下のコードを機密情報漏洩の観点から分析してください。

**対象リポジトリ**: ${repository}

以下のパターンを検索してください：
- APIキー、トークン
- パスワード
- 秘密鍵
- データベース接続文字列
- AWS/Azure/GCPの認証情報

発見した機密情報と適切な管理方法を報告してください。
```

| フィールド | 説明 |
|-----------|------|
| `name` | スキル名（省略時はディレクトリ名がデフォルト） |
| `description` | スキルの説明 |
| `metadata.agent` | スキルを紐付けるエージェント（例: `security`, `code-quality`）。省略時は全エージェントで利用可能 |
| 本文 | プロンプトテンプレート。`${paramName}` プレースホルダーが実行時に置換される |

## GraalVM Native Image

ネイティブバイナリとしてビルドする場合:

```bash
# ネイティブイメージをビルド
mvn clean package -Pnative

# 実行
./target/review run --repo owner/repository --all
```

### リフレクション設定の生成（初回ビルド時・依存関係更新時）

Copilot SDK は内部で Jackson Databind を使用して JSON-RPC 通信を行います。GraalVM Native Image ではリフレクションが制限されるため、SDK 内部の DTO クラス群に対してリフレクション設定を事前に登録する必要があります。

設定が不足している場合、Native Image の実行時に Copilot CLI との通信でタイムアウトが発生します（FAT JAR では発生しません）。これは Jackson がリフレクション経由で JSON のシリアライズ/デシリアライズを行う際に、Native Image 環境では未登録クラスのメタデータにアクセスできず、SDK 内部で例外が握り潰されて `CompletableFuture` が完了しなくなるためです。

GraalVM の**トレーシングエージェント**を使って、実際の実行で必要なリフレクション情報を自動収集してください。

```bash
# 1. まず FAT JAR をビルド
mvn clean package -DskipTests

# 2. トレーシングエージェント付きで実行し、リフレクション設定を自動生成
#    既存の設定とマージするため config-merge-dir を使用
java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image \
     -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
     run --repo owner/repository --all

# 3. 生成された設定を確認
ls src/main/resources/META-INF/native-image/
# reflect-config.json, resource-config.json, proxy-config.json 等が生成・更新される

# 4. Native Image を再ビルド
mvn clean package -Pnative -DskipTests
```

> **注意**: `config-output-dir` ではなく `config-merge-dir` を使うことで、既存の設定（Logback 等）を上書きせずマージできます。また、レビュー対象の全エージェント（security, performance 等）を一通り実行し、すべてのコードパスを通すことで、漏れのない設定を生成できます。

> **ヒント**: Copilot SDK や Jackson 等の依存ライブラリを更新した場合も、トレーシングエージェントを再実行して設定を更新してください。

## アーキテクチャ

```mermaid
flowchart TB
    subgraph CLI["CLIレイヤー"]
        ReviewApp[ReviewApp<br/>エントリポイント]
        ReviewCommand[ReviewCommand]
        ListAgentsCommand[ListAgentsCommand]
        SkillCommand[SkillCommand]
    end

    subgraph Services["サービスレイヤー"]
        AgentService[AgentService]
        CopilotService[CopilotService]
        ReviewService[ReviewService]
        ReportService[ReportService]
        SkillService[SkillService]
        TemplateService[TemplateService]
    end

    subgraph Orchestrator["オーケストレータ"]
        ReviewOrchestrator[ReviewOrchestrator<br/>仮想スレッド /<br/>Structured Concurrency]
    end

    subgraph Agents["レビューエージェント"]
        Security[Security Agent]
        CodeQuality[Code Quality Agent]
        Performance[Performance Agent]
        BestPractices[Best Practices Agent]
    end

    subgraph Skills["スキル実行"]
        SkillExecutor[SkillExecutor<br/>Structured Concurrency]
        SkillRegistry[SkillRegistry]
    end

    subgraph Target["レビュー対象"]
        ReviewTarget["ReviewTarget<br/>(sealed interface)"]
        GitHubTarget[GitHubTarget]
        LocalTarget[LocalTarget]
        LocalFileProvider[LocalFileProvider]
    end

    subgraph Reports["レポート生成"]
        ReportGenerator[ReportGenerator]
        SummaryGenerator[SummaryGenerator]
        FindingsExtractor[FindingsExtractor]
        ContentSanitizer[ContentSanitizer]
    end

    subgraph Instructions["カスタムインストラクション"]
        CustomInstructionLoader[CustomInstructionLoader]
    end

    subgraph External["外部サービス"]
        Copilot[GitHub Copilot API<br/>LLM]
        GitHubMCP[GitHub MCP Server]
    end

    ReviewApp --> ReviewCommand & ListAgentsCommand & SkillCommand
    ReviewCommand --> ReviewService
    ReviewCommand --> ReportService
    ListAgentsCommand --> AgentService
    SkillCommand --> SkillService

    ReviewService --> ReviewOrchestrator
    ReviewService --> CustomInstructionLoader
    ReviewOrchestrator --> Agents
    ReportService --> ReportGenerator & SummaryGenerator
    SkillService --> SkillExecutor
    SkillService --> SkillRegistry

    Agents --> ContentSanitizer
    SummaryGenerator --> FindingsExtractor

    ReviewTarget --> GitHubTarget & LocalTarget
    LocalTarget --> LocalFileProvider

    CopilotService --> Copilot
    Agents -.-> Copilot
    Agents -.-> GitHubMCP
    SkillExecutor -.-> Copilot
    SkillExecutor -.-> GitHubMCP
    SummaryGenerator -.-> Copilot
```

## テンプレートのカスタマイズ

レポートやサマリーのフォーマットは、テンプレートファイルで外部化されています。

### テンプレートディレクトリ

デフォルトでは `templates/` ディレクトリ内のテンプレートが使用されます。

```
templates/
├── summary-system.md              # サマリー生成システムプロンプト
├── summary-prompt.md              # サマリー生成ユーザープロンプト
├── summary-result-entry.md        # サマリー結果エントリ（成功時）
├── summary-result-error-entry.md  # サマリー結果エントリ（失敗時）
├── default-output-format.md       # デフォルト出力フォーマット
├── output-constraints.md          # 出力制約（CoT抑制・言語指定）
├── report.md                      # 個別レポートテンプレート
├── report-link-entry.md           # レポートリンクエントリ
├── executive-summary.md           # エグゼクティブサマリーテンプレート
├── fallback-summary.md            # フォールバックサマリーテンプレート
├── fallback-agent-row.md          # フォールバックテーブル行
├── fallback-agent-success.md      # フォールバック成功詳細
├── fallback-agent-failure.md      # フォールバック失敗詳細
├── local-review-content.md        # ローカルレビューコンテンツ
├── custom-instruction-section.md  # カスタムインストラクションセクション
└── review-custom-instruction.md   # レビュー用カスタムインストラクション
```

### テンプレート設定

`application.yml` でテンプレートパスをカスタマイズできます:

```yaml
reviewer:
  templates:
    directory: templates                    # テンプレートディレクトリ
    summary-system-prompt: summary-system.md
    summary-user-prompt: summary-prompt.md
    default-output-format: default-output-format.md
    output-constraints: output-constraints.md  # 出力制約（CoT抑制・言語指定）
    report: report.md
    executive-summary: executive-summary.md
    fallback-summary: fallback-summary.md
    local-review-content: local-review-content.md
    summary-result-entry: summary-result-entry.md
    summary-result-error-entry: summary-result-error-entry.md
    fallback-agent-row: fallback-agent-row.md
    fallback-agent-success: fallback-agent-success.md
    fallback-agent-failure: fallback-agent-failure.md
    report-link-entry: report-link-entry.md
```

### プレースホルダー

テンプレート内では `{{placeholder}}` 形式のプレースホルダーが使用できます。各テンプレートで使用可能なプレースホルダーはテンプレートファイルを参照してください。

## プロジェクト構造

```
multi-agent-reviewer/
├── pom.xml                              # Maven設定
├── .sdkmanrc                            # SDKMAN GraalVM設定
├── .github/
│   └── skills/                          # スキル定義（SKILL.md形式）
│       ├── sql-injection-check/
│       ├── secret-scan/
│       └── ...
├── agents/                              # エージェント定義（.agent.md形式）
│   ├── security.agent.md
│   ├── code-quality.agent.md
│   ├── performance.agent.md
│   └── best-practices.agent.md
├── templates/                           # テンプレートファイル
│   ├── summary-system.md
│   ├── summary-prompt.md
│   ├── report.md
│   └── ...
└── src/main/java/dev/logicojp/reviewer/
    ├── ReviewApp.java                   # CLIエントリポイント
    ├── ReviewCommand.java               # reviewサブコマンド
    ├── ListAgentsCommand.java           # listサブコマンド
    ├── SkillCommand.java                # skillサブコマンド
    ├── agent/
    │   ├── AgentConfig.java             # 設定モデル
    │   ├── AgentConfigLoader.java       # 設定読込
    │   ├── AgentMarkdownParser.java     # .agent.md パーサー
    │   └── ReviewAgent.java             # レビューエージェント
    ├── cli/
    │   ├── CliParsing.java              # CLIオプション解析
    │   ├── CliUsage.java                # ヘルプ・使い方表示
    │   ├── CliValidationException.java  # CLI入力バリデーション例外
    │   └── ExitCodes.java               # 終了コード定数
    ├── config/
    │   ├── ModelConfig.java             # LLMモデル設定
    │   ├── ExecutionConfig.java         # 実行設定
    │   ├── GithubMcpConfig.java         # GitHub MCP設定
    │   ├── OrchestratorConfig.java      # オーケストレータ設定
    │   ├── SkillConfig.java             # スキル設定
    │   └── TemplateConfig.java          # テンプレート設定
    ├── instruction/
    │   ├── CustomInstruction.java       # カスタムインストラクションモデル
    │   ├── CustomInstructionLoader.java # インストラクション読込
    │   └── InstructionSource.java       # ソース種別
    ├── orchestrator/
    │   └── ReviewOrchestrator.java      # 並列実行制御
    ├── report/
    │   ├── ContentSanitizer.java        # LLM前置き文/CoT除去
    │   ├── FindingsExtractor.java       # 指摘事項抽出
    │   ├── ReviewResult.java            # 結果モデル
    │   ├── ReportGenerator.java         # 個別レポート生成
    │   └── SummaryGenerator.java        # サマリー生成
    ├── service/
    │   ├── AgentService.java            # エージェント管理
    │   ├── CopilotService.java          # Copilot SDK連携
    │   ├── ReportService.java           # レポート生成
    │   ├── ReviewService.java           # レビュー実行
    │   ├── SkillService.java            # スキル管理
    │   └── TemplateService.java         # テンプレート読込
    ├── skill/
    │   ├── SkillDefinition.java         # スキル定義モデル
    │   ├── SkillMarkdownParser.java     # スキルMarkdownパーサー
    │   ├── SkillParameter.java          # スキルパラメータモデル
    │   ├── SkillRegistry.java           # スキルレジストリ
    │   ├── SkillExecutor.java           # スキル実行
    │   └── SkillResult.java             # スキル結果モデル
    ├── target/
    │   ├── ReviewTarget.java            # レビュー対象（sealed interface）
    │   └── LocalFileProvider.java       # ローカルファイル収集
    └── util/
        ├── FileExtensionUtils.java      # ファイル拡張子ユーティリティ
        └── GitHubTokenResolver.java     # GitHubトークン解決
```

## ライセンス

MIT License
