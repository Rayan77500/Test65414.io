// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.runner.lifecycle;
public interface ActivityLifecycleCallback {
    void onActivityLifecycleChanged(android.app.Activity activity, Stage stage);
}
