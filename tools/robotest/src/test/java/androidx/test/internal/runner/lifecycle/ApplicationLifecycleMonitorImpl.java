// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.internal.runner.lifecycle;
public class ApplicationLifecycleMonitorImpl implements androidx.test.runner.lifecycle.ApplicationLifecycleMonitor {
    public ApplicationLifecycleMonitorImpl() { }
    public void signalLifecycleChange(android.app.Application app, androidx.test.runner.lifecycle.ApplicationStage stage) { }
}
