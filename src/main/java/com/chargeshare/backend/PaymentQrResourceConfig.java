
package com.chargeshare.backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class PaymentQrResourceConfig implements WebMvcConfigurer {

    private static final Path QR_UPLOAD_DIR =
            Paths.get("uploads", "payment-qr")
                    .toAbsolutePath()
                    .normalize();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/payment-qr/**")
                .addResourceLocations(
                        QR_UPLOAD_DIR.toUri().toString()
                );
    }
}
