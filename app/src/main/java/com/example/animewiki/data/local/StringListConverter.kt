package com.example.animewiki.data.local

import androidx.room.TypeConverter

class StringListConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split("|||")

    @TypeConverter
    fun fromList(list: List<String>?): String =
        list?.joinToString("|||") ?: ""
}
