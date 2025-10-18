package iaf.ofek.sigma.format;

/**
 * Thread-local context for storing the current request's time format preference
 * 
 * Used by the interceptor to store the format, and by the response formatter to retrieve it
 * ThreadLocal ensures thread-safety in multi-threaded servlet environment
 */
public class TimeFormatContext {
    
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    
    /**
     * Sets the time format for the current request thread
     */
    public static void setFormat(String format) {
        CONTEXT.set(format);
    }
    
    /**
     * Gets the time format for the current request thread
     * Returns null if not set
     */
    public static String getFormat() {
        return CONTEXT.get();
    }
    
    /**
     * Clears the time format for the current request thread
     * MUST be called after request processing to prevent memory leaks
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
