package indi.dmzz_yyhyy.lightnovelreader.data.book

import indi.dmzz_yyhyy.lightnovelreader.utils.CanBeEmpty


data class BookVolumes(
    val bookId: Int,
    val volumes: List<Volume>
): CanBeEmpty {
    companion object {
        fun empty() = BookVolumes(-1, emptyList())
        fun empty(bookId: Int) = BookVolumes(bookId, emptyList())
    }

    override fun isEmpty(): Boolean = volumes.isEmpty()
}
