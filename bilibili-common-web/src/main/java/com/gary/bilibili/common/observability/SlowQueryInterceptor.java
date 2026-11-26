package com.gary.bilibili.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnClass(Executor.class)
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowQueryInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryInterceptor.class);

    private final MeterRegistry meterRegistry;
    private final long slowThresholdNanos;

    public SlowQueryInterceptor(MeterRegistry meterRegistry,
                                @Value("${performance.slow-sql-threshold:300ms}") Duration slowThreshold) {
        this.meterRegistry = meterRegistry;
        this.slowThresholdNanos = Math.max(Duration.ofMillis(10).toNanos(), slowThreshold.toNanos());
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        long started = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.nanoTime() - started;
            meterRegistry.timer("bilibili.sql.duration", "statement", statement.getId())
                    .record(elapsed, TimeUnit.NANOSECONDS);
            if (elapsed >= slowThresholdNanos) {
                log.warn("Slow SQL statement={} durationMs={}", statement.getId(),
                        TimeUnit.NANOSECONDS.toMillis(elapsed));
            }
        }
    }
}
