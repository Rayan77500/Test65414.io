// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.runner.lifecycle;
public final class ActivityLifecycleMonitorRegistry {
    private static ActivityLifecycleMonitor m;
    public static void registerInstance(ActivityLifecycleMonitor i) { m = i; }
    public static ActivityLifecycleMonitor getInstance() { return m; }
}
