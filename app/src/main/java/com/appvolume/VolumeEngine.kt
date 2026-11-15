package com.appvolume

import android.content.Context
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class AudioApp(val pkg: String, val label: String, val playing: Boolean, val volume: Float)

object VolumeEngine {
    private lateinit var ctx: Context
    private val prefs by lazy { ctx.getSharedPreferences("vol", 0) }
    private var svc: Any? = null
    private val seen = LinkedHashMap<String, String>()
    val apps = MutableStateFlow<List<AudioApp>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    fun init(c: Context) { ctx = c.applicationContext }

    fun volume(pkg: String) = prefs.getFloat(pkg, 1f)

    fun setVolume(pkg: String, v: Float) {
        prefs.edit().putFloat(pkg, v).apply()
        apps.value = apps.value.map { if (it.pkg == pkg) it.copy(volume = v) else it }
    }

    @Synchronized
    fun tick() {
        try {
            if (svc == null) {
                val b = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("audio"))
                svc = Class.forName("android.media.IAudioService\$Stub")
                    .getMethod("asInterface", IBinder::class.java).invoke(null, b)
            }
            val list = Class.forName("android.media.IAudioService")
                .getMethod("getActivePlaybackConfigurations").invoke(svc) as List<*>
            val pm = ctx.packageManager
            val playing = HashSet<String>()
            for (c in list) {
                if (c == null) continue
                val k = c.javaClass
                val uid = k.getMethod("getClientUid").invoke(c) as Int
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: continue
                if (pkg == ctx.packageName || pkg == "android") continue
                if ((k.getMethod("getPlayerState").invoke(c) as Int) == 2) playing += pkg
                if (!seen.containsKey(pkg)) seen[pkg] = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) { pkg }
                if (prefs.contains(pkg)) {
                    val p = k.getMethod("getPlayerProxy").invoke(c)
                    p?.javaClass?.getMethod("setVolume", Float::class.javaPrimitiveType)
                        ?.invoke(p, volume(pkg))
                }
            }
            apps.value = seen.filter { it.key in playing || prefs.contains(it.key) }
                .map { AudioApp(it.key, it.value, it.key in playing, volume(it.key)) }
            error.value = null
        } catch (e: Throwable) {
            svc = null
            error.value = (e.cause ?: e).toString()
        }
    }
}
