package zw.test.billpay.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the generated OpenAPI spec (/v3/api-docs) and Swagger UI (/swagger-ui.html).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI billpayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BillPay Service API")
                .version("v1")
                .description("Pay a bill through the external biller gateway and check whether the payment went through."));
    }
}
