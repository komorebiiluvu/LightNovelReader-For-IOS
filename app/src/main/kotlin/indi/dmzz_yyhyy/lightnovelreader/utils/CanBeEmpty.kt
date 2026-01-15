package indi.dmzz_yyhyy.lightnovelreader.utils

interface CanBeEmpty {
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean = !isEmpty()
}