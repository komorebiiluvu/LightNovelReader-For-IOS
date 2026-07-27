package indi.dmzz_yyhyy.lightnovelreader.data.plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nightfish.lightnovelreader.api.ui.ActivityProviderApi
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopActivityProvider @Inject constructor(
    @ApplicationContext context: Context
) : ActivityProviderApi, Application.ActivityLifecycleCallbacks {
    @Volatile
    private var topActivityReference = WeakReference<Activity>(null)

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun getTopActivity(): Activity? = topActivityReference.get()

    override fun onActivityResumed(activity: Activity) {
        topActivityReference = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        clearIfTop(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        clearIfTop(activity)
    }

    private fun clearIfTop(activity: Activity) {
        if (topActivityReference.get() === activity) {
            topActivityReference.clear()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
