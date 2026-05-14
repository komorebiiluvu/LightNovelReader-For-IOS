package indi.dmzz_yyhyy.lightnovelreader.data.local.cbor.mark

enum class BackupTable(val id: Int) {
    BOOK_INFORMATION(1),
    BOOK_RECORD(2),
    DAILY_COUNT(3),
    BOOKSHELF(4),
    BOOKSHELF_BOOK_METADATA(5),
    CHAPTER_CONTENT(6),
    CHAPTER_INFORMATION(7),
    FORMATTING_RULE(8),
    USER_DATA(9),
    USER_READING_DATA(10),
    VOLUME(11);

    companion object {
        fun fromId(id: Int): BackupTable? {
            return entries.find { it.id == id }
        }
    }
}