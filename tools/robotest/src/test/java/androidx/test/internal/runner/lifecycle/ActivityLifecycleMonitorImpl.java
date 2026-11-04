// Bouchon minimal (androidx.test:monitor est indisponible hors du depot Google).
package androidx.test.internal.runner.lifecycle;
import android.app.Activity;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitor;
import androidx.test.runner.lifecycle.Stage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
public class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
    private final Map<Activity, Stage> stages = new WeakHashMap<Activity, Stage>();
    private final List<ActivityLifecycleCallback> callbacks = new ArrayList<ActivityLifecycleCallback>();
    public ActivityLifecycleMonitorImpl() { }
    public synchronized void signalLifecycleChange(Stage stage, Activity activity) {
        stages.put(activity, stage);
        for (ActivityLifecycleCallback c : new ArrayList<ActivityLifecycleCallback>(callbacks)) c.onActivityLifecycleChanged(activity, stage);
    }
    public synchronized void addLifecycleCallback(ActivityLifecycleCallback c) { callbacks.add(c); }
    public synchronized void removeLifecycleCallback(ActivityLifecycleCallback c) { callbacks.remove(c); }
    public synchronized Stage getLifecycleStageOf(Activity a) { return stages.get(a); }
    public synchronized Collection<Activity> getActivitiesInStage(Stage s) {
        List<Activity> out = new ArrayList<Activity>();
        for (Map.Entry<Activity, Stage> e : stages.entrySet()) if (e.getValue() == s) out.add(e.getKey());
        return out;
    }
}
