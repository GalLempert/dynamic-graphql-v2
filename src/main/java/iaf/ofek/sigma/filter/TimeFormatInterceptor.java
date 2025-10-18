package iaf.ofek.sigma.filter;

import iaf.ofek.sigma.format.TimeFormatContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to extract time format preference from HTTP header
 * 
 * Reads "X-Time-Format" header and stores it in ThreadLocal context
 * Ensures cleanup after request to prevent memory leaks
 */
@Component
public class TimeFormatInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(TimeFormatInterceptor.class);
    private static final String TIME_FORMAT_HEADER = "X-Time-Format";
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        String timeFormat = request.getHeader(TIME_FORMAT_HEADER);
        
        if (timeFormat != null && !timeFormat.isBlank()) {
            logger.debug("Time format requested: {}", timeFormat);
            TimeFormatContext.setFormat(timeFormat);
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        // Clean up ThreadLocal to prevent memory leaks
        TimeFormatContext.clear();
    }
}
