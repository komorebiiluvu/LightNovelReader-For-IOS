package io.nightfish.lightnovelreader.api.ui

import android.app.Activity

/**
 * 提供宿主应用当前前台 [Activity] 的访问能力。
 *
 * 插件可以通过构造器注入此接口。对返回的 [Activity] 不应长期持有，
 * 且所有界面操作都应在主线程执行。
 *
 * @since Api 4
 */
interface ActivityProviderApi {
    /**
     * 获取当前处于 resumed 状态的宿主 [Activity]。
     *
     * 当应用位于后台、Activity 正在切换或尚未创建时返回 `null`。
     *
     * @since Api 4
     */
    fun getTopActivity(): Activity?
}
