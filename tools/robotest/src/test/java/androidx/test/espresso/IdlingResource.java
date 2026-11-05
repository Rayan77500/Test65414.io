// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.espresso;
public interface IdlingResource {
    String getName();
    boolean isIdleNow();
    void registerIdleTransitionCallback(ResourceCallback callback);
    interface ResourceCallback { void onTransitionToIdle(); }
}
