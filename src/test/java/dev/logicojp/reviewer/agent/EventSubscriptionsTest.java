package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventSubscriptions")
class EventSubscriptionsTest {

    @Nested
    @DisplayName("closeAll")
    class CloseAll {

        @Test
        @DisplayName("全てのサブスクリプションをクローズする")
        void closesAllSubscriptions() {
            AtomicInteger closeCount = new AtomicInteger(0);
            AutoCloseable sub = closeCount::incrementAndGet;
            var subscriptions = new EventSubscriptions(sub, sub, sub, sub);

            subscriptions.closeAll();

            assertThat(closeCount.get()).isEqualTo(4);
        }

        @Test
        @DisplayName("一つのサブスクリプションが例外を投げても他はクローズされる")
        void continuesClosingOnException() {
            AtomicInteger closeCount = new AtomicInteger(0);
            AutoCloseable failing = () -> { throw new RuntimeException("test error"); };
            AutoCloseable counting = closeCount::incrementAndGet;

            var subscriptions = new EventSubscriptions(failing, counting, failing, counting);

            subscriptions.closeAll();

            assertThat(closeCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("全てのサブスクリプションが例外を投げても例外は伝播しない")
        void doesNotPropagateExceptions() {
            AutoCloseable failing = () -> { throw new RuntimeException("test error"); };
            var subscriptions = new EventSubscriptions(failing, failing, failing, failing);

            // Should not throw
            subscriptions.closeAll();
        }
    }

    @Nested
    @DisplayName("レコードフィールド")
    class RecordFields {

        @Test
        @DisplayName("各フィールドにアクセスできる")
        void accessFields() {
            AutoCloseable a = () -> {};
            AutoCloseable b = () -> {};
            AutoCloseable c = () -> {};
            AutoCloseable d = () -> {};
            var subscriptions = new EventSubscriptions(a, b, c, d);

            assertThat(subscriptions.allEvents()).isSameAs(a);
            assertThat(subscriptions.messages()).isSameAs(b);
            assertThat(subscriptions.idle()).isSameAs(c);
            assertThat(subscriptions.error()).isSameAs(d);
        }

        @Test
        @DisplayName("コンストラクタはnullサブスクリプションを拒否する")
        void rejectsNullSubscription() {
            AutoCloseable noOp = () -> {};

            assertThatThrownBy(() -> new EventSubscriptions(null, noOp, noOp, noOp))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allEvents must not be null");
        }
    }
}
