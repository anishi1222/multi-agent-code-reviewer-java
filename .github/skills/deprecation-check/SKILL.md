---
name: deprecation-check
description: 非推奨のAPIや機能の使用を検出し、代替APIへの移行方法を提案します。@Deprecatedアノテーションや将来削除予定のAPIを対象にします。
metadata:
  agent: best-practices
---

# 非推奨API検出

以下のリポジトリ内の非推奨API使用を検出してください。

**対象リポジトリ**: ${repository}

以下を検出してください：
- @Deprecatedアノテーションが付いたAPIの使用
- 将来削除予定のAPI
- 古いバージョンのみで利用可能な機能

代替APIへの移行方法を報告してください。
