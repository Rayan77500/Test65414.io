// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.runner.lifecycle;
public final class ApplicationLifecycleMonitorRegistry {
    private static ApplicationLifecycleMonitor m;
    public static void registerInstance(ApplicationLifecycleMonitor i) { m = i; }
    public static ApplicationLifecycleMonitor getInstance() { return m; }
}
