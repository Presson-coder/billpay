package zw.test.billpay.gateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gateway settings, bound from billpay.gateway.* properties.
 *
 * @param timeout
 * @param poolSize
 * @param queueCapacity
 */
@ConfigurationProperties(prefix = "billpay.gateway")
public record GatewayProperties(
                @DefaultValue("3s") Duration timeout,
                @DefaultValue("10") int poolSize,
                @DefaultValue("50") int queueCapacity) {
}
