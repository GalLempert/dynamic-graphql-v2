package iaf.ofek.sigma.config;

import iaf.ofek.sigma.filter.TimeFormatInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration
 * Registers interceptors and other web-layer components
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    private final TimeFormatInterceptor timeFormatInterceptor;
    
    public WebConfig(TimeFormatInterceptor timeFormatInterceptor) {
        this.timeFormatInterceptor = timeFormatInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(timeFormatInterceptor);
    }
}
