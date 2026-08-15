package com.autotask.app.picker

import com.github.promeg.pinyinhelper.Pinyin

/**
 * 应用名拼音工具（选择器排序/搜索用）。
 * 基于 TinyPinyin：汉字→拼音，非汉字原样保留。
 */
object AppPinyin {

    /** 首字母分组：A-Z；非字母归 "#" */
    fun groupLetter(text: String): String {
        val first = firstLetter(text)
        return if (first.length == 1 && first[0] in 'A'..'Z') first else "#"
    }

    /** 首个字符的拼音首字母（大写）；非汉字返回原字符大写 */
    fun firstLetter(text: String): String {
        if (text.isEmpty()) return "#"
        val ch = text.first()
        val py = Pinyin.toPinyin(ch).uppercase()
        return py.firstOrNull()?.toString() ?: "#"
    }

    /** 完整小写拼音串（用于组内排序与搜索）："微信" -> "weixin"，"QQ" -> "qq" */
    fun fullPinyin(text: String): String =
        text.map { Pinyin.toPinyin(it).lowercase() }.joinToString("")

    /** 排序键：首字母 + 拼音，保证分组内按拼音序 */
    fun sortKey(text: String): String = groupLetter(text) + fullPinyin(text)
}
