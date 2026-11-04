// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.runner.intent;
public interface IntentStubber {
    android.app.Instrumentation.ActivityResult getActivityResultForIntent(android.content.Intent intent);
}
