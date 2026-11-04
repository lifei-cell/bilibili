package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(15)
class MediaProcessRunnerTest {
    @Test
    void timeoutReapsProcessBeforeReturning() throws Exception {
        Process process = start("sleep");
        try {
            assertThatThrownBy(() -> MediaProcessRunner.await(process, 1, "media timeout"))
                    .isInstanceOf(BusinessException.class).hasMessage("media timeout");
            assertThat(process.isAlive()).isFalse();
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void interruptionAlsoReapsProcess() throws Exception {
        Process process = start("sleep");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch waiting = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            waiting.countDown();
            try {
                MediaProcessRunner.await(process, 60, "unexpected timeout");
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        try {
            thread.start();
            assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
            thread.interrupt();
            thread.join(7000);
            assertThat(thread.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(InterruptedException.class);
            assertThat(process.isAlive()).isFalse();
        } finally {
            process.destroyForcibly();
            thread.interrupt();
        }
    }

    @Test
    void retainsNonzeroExitCode() throws Exception {
        assertThat(MediaProcessRunner.await(start("exit"), 10, "unexpected timeout")).isEqualTo(73);
    }

    private Process start(String mode) throws Exception {
        String binary = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classpath = Path.of(MediaProcessRunnerTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).toString();
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", binary).toString(),
                "-cp", classpath, Child.class.getName(), mode).start();
    }

    public static class Child {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("exit")) System.exit(73);
            Thread.sleep(60_000);
        }
    }
}
