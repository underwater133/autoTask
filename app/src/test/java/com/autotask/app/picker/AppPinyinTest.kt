package com.autotask.app.picker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AppPinyin 单元测试：首字母分组、完整拼音、排序键。
 */
class AppPinyinTest {

    @Test
    fun groupLetter_chinese() {
        assertEquals("W", AppPinyin.groupLetter("微信"))
        assertEquals("Z", AppPinyin.groupLetter("支付宝"))
        assertEquals("T", AppPinyin.groupLetter("淘宝"))
    }

    @Test
    fun groupLetter_englishAndDigits() {
        assertEquals("Q", AppPinyin.groupLetter("QQ"))
        assertEquals("B", AppPinyin.groupLetter("bilibili"))
        assertEquals("#", AppPinyin.groupLetter("12306"))
        assertEquals("#", AppPinyin.groupLetter("@工具"))
    }

    @Test
    fun firstLetter_mixed() {
        assertEquals("W", AppPinyin.firstLetter("微信"))
        assertEquals("Q", AppPinyin.firstLetter("qq"))
        assertEquals("#", AppPinyin.firstLetter(""))
        assertEquals("1", AppPinyin.firstLetter("123"))
    }

    @Test
    fun fullPinyin_lowercaseConcatenated() {
        assertEquals("weixin", AppPinyin.fullPinyin("微信"))
        assertEquals("qq", AppPinyin.fullPinyin("QQ"))
        assertEquals("zhifubao", AppPinyin.fullPinyin("支付宝"))
    }

    @Test
    fun sortKey_groupsThenPinyin() {
        val apps = listOf("微信", "支付宝", "QQ", "哔哩哔哩")
        val sorted = apps.sortedBy { AppPinyin.sortKey(it) }
        // B组(哔哩哔哩) < Q组(QQ) < W组(微信) < Z组(支付宝)
        assertEquals(listOf("哔哩哔哩", "QQ", "微信", "支付宝"), sorted)
    }

    @Test
    fun groupOrdering_sameLetter_sortedByPinyin() {
        val apps = listOf("百度", "哔哩哔哩", "不背单词", "哔站")
        val sorted = apps.sortedBy { AppPinyin.sortKey(it) }
        // 全部 B 组，按完整拼音：baidu < bilibili < bizhan < bubeidanci
        assertEquals(listOf("百度", "哔哩哔哩", "哔站", "不背单词"), sorted)
    }
}
