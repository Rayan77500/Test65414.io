// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.runner.intent;
public final class IntentStubberRegistry {
    public static boolean isLoaded() { return false; }
    public static IntentStubber getInstance() { return null; }
}
