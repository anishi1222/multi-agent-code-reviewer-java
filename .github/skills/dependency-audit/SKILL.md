---
name: dependency-audit
description: プロジェクトの依存関係に既知の脆弱性がないか確認します。pom.xml、package.json、requirements.txt等の依存関係定義ファイルを分析します。
metadata:
  agent: security
---

# 依存関係セキュリティ監査

以下のプロジェクトの依存関係をセキュリティの観点から分析してください。

**対象リポジトリ**: ${repository}

以下を確認してください：
- pom.xml, package.json, requirements.txt などの依存関係定義ファイル
- 既知の脆弱性（CVE）を持つライブラリ
- 古いバージョンのライブラリ
- 推奨されるアップデート

発見した問題と推奨対応を報告してください。
