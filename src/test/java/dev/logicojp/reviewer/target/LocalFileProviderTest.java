package dev.logicojp.reviewer.target;

import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LocalFileProvider")
class LocalFileProviderTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("collectFiles")
    class CollectFiles {

        @Test
        @DisplayName("Javaファイルを収集できる")
        void collectsJavaFiles() throws IOException {
            Files.writeString(tempDir.resolve("Main.java"), "public class Main {}");
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().relativePath()).isEqualTo("Main.java");
        }

        @Test
        @DisplayName("無視ディレクトリ内のファイルを除外する")
        void skipsIgnoredDirectories() throws IOException {
            Path gitDir = tempDir.resolve(".git");
            Files.createDirectories(gitDir);
            Files.writeString(gitDir.resolve("config.java"), "class X {}");
            Files.writeString(tempDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().relativePath()).isEqualTo("App.java");
        }

        @Test
        @DisplayName("センシティブファイルを除外する")
        void excludesSensitiveFiles() throws IOException {
            Files.writeString(tempDir.resolve(".env"), "SECRET=abc");
            Files.writeString(tempDir.resolve("id_rsa.pem"), "key");
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().relativePath()).isEqualTo("Main.java");
        }

        @Test
        @DisplayName("追加のセンシティブファイルパターンが機能する")
        void excludesAdditionalSensitivePatterns() throws IOException {
            Files.writeString(tempDir.resolve("application-dev.yml"), "db: secret");
            Files.writeString(tempDir.resolve(".env.local"), "LOCAL_KEY=abc");
            Files.writeString(tempDir.resolve(".env.production"), "PROD_KEY=xyz");
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().relativePath()).isEqualTo("Main.java");
        }

        @Test
        @DisplayName("ソースファイル拡張子に一致しないファイルを除外する")
        void excludesNonSourceFiles() throws IOException {
            Files.writeString(tempDir.resolve("image.png"), "binary");
            Files.writeString(tempDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).hasSize(1);
        }

        @Test
        @DisplayName("空のディレクトリからは空リストを返す")
        void emptyDirectoryReturnsEmpty() {
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("サブディレクトリ内のファイルも収集する")
        void collectsFromSubdirectories() throws IOException {
            Path subDir = tempDir.resolve("src/main");
            Files.createDirectories(subDir);
            Files.writeString(subDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().relativePath()).contains("src/main/App.java");
        }

        @Test
        @DisplayName("設定で許可した拡張子のみを収集する")
        void collectsOnlyConfiguredExtensions() throws IOException {
            Files.writeString(tempDir.resolve("readme.txt"), "ok");
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

            LocalFileConfig config = new LocalFileConfig(
                1024,
                4096,
                List.of(".git"),
                List.of("txt"),
                List.of(".env"),
                List.of("pem")
            );
            LocalFileProvider provider = new LocalFileProvider(tempDir, config);
            List<LocalFileProvider.LocalFile> files = provider.collectFiles();

            assertThat(files).hasSize(1);
            assertThat(files.getFirst().relativePath()).isEqualTo("readme.txt");
        }
    }

    @Nested
    @DisplayName("generateReviewContent")
    class GenerateReviewContent {

        @Test
        @DisplayName("空のファイルリストからはno source filesメッセージを返す")
        void emptyFilesReturnsNoSourceMessage() {
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            String content = provider.generateReviewContent(List.of());
            assertThat(content).contains("no source files found");
        }

        @Test
        @DisplayName("nullのファイルリストからはno source filesメッセージを返す")
        void nullFilesReturnsNoSourceMessage() {
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            String content = provider.generateReviewContent(null);
            assertThat(content).contains("no source files found");
        }

        @Test
        @DisplayName("ファイルコンテンツをMarkdownコードブロックでラップする")
        void wrapsInCodeBlocks() {
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            var file = new LocalFileProvider.LocalFile("Main.java", "public class Main {}", 20);
            String content = provider.generateReviewContent(List.of(file));
            assertThat(content).contains("### Main.java");
            assertThat(content).contains("```java");
            assertThat(content).contains("public class Main {}");
        }
    }

    @Nested
    @DisplayName("generateDirectorySummary")
    class GenerateDirectorySummary {

        @Test
        @DisplayName("空のファイルリストからはno source filesメッセージを返す")
        void emptyFilesReturnsMessage() {
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            String summary = provider.generateDirectorySummary(List.of());
            assertThat(summary).contains("No source files found");
        }

        @Test
        @DisplayName("ファイル数とサイズ情報を含む")
        void includesCountAndSize() {
            LocalFileProvider provider = new LocalFileProvider(tempDir);
            var file = new LocalFileProvider.LocalFile("App.java", "class App {}", 12);
            String summary = provider.generateDirectorySummary(List.of(file));
            assertThat(summary).contains("Files: 1");
            assertThat(summary).contains("12 bytes");
        }
    }

    @Nested
    @DisplayName("コンストラクタ")
    class ConstructorTests {

        @Test
        @DisplayName("nullのベースディレクトリはIllegalArgumentExceptionをスローする")
        void nullBaseDirectoryThrows() {
            assertThatThrownBy(() -> new LocalFileProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
