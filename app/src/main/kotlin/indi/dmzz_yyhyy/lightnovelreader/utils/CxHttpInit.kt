package indi.dmzz_yyhyy.lightnovelreader.utils

import cxhttp.CxHttpHelper
import cxhttp.converter.GsonConverter
import kotlinx.coroutines.MainScope

object CxHttpInit {
    private var isInit = false

    fun init() {
        if (isInit) return
        CxHttpHelper.init(scope = MainScope(), debugLog = true, converter = GsonConverter())
        isInit = true
    }
}