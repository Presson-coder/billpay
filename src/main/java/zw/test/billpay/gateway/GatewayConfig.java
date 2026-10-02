package zw.test.billpay.gateway;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfig {

    /**
     * Dedicated, bounded pool for gateway calls so a slow biller cannot exhaust the
     * web server's threads.
     */
    @Bean(name = "gatewayExecutor", destroyMethod = "shutdownNow")
    public ExecutorService gatewayExecutor(GatewayProperties properties) {
        return new ThreadPoolExecutor(
                properties.poolSize(),
                properties.poolSize(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                namedDaemonThreads("biller-gateway-"));
    }

    private static ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
