package io.legado.app.ui.association

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonToken
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import java.io.FilterReader
import java.io.Reader

internal data class BookSourceImportData(
    val sources: List<BookSource>,
    val sourceUrls: List<String> = emptyList()
)

/** Holds only one JSON tree at a time; the preview still retains the parsed source models. */
internal fun readBookSourceImport(input: Reader, checkActive: () -> Unit = {}): BookSourceImportData {
    val checked = object : FilterReader(input) {
        override fun read(): Int { checkActive(); return super.read() }
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            checkActive()
            return super.read(buffer, offset, length)
        }
    }
    GSON.newJsonReader(checked).use { reader ->
        val sources = arrayListOf<BookSource>()
        var urls = emptyList<String>()
        fun readSource(): BookSource {
            checkActive()
            val source = GSON.fromJson<BookSource?>(reader, BookSource::class.java)
                ?: throw JsonSyntaxException("Nguồn truyện không được là null")
            require(source.bookSourceUrl.isNotBlank()) { "Nguồn truyện thiếu địa chỉ" }
            return source
        }
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) sources.add(readSource())
                reader.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                checkActive()
                val obj = JsonParser.parseReader(reader).asJsonObject
                if (obj.has("sourceUrls")) {
                    val array = obj.getAsJsonArray("sourceUrls")
                        ?: throw JsonSyntaxException("sourceUrls phải là danh sách")
                    urls = array.map {
                        checkActive()
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank()) {
                            "Địa chỉ đăng ký nguồn không hợp lệ"
                        }
                        it.asString
                    }
                } else {
                    val source = GSON.fromJson(obj, BookSource::class.java)
                    require(source.bookSourceUrl.isNotBlank()) { "Nguồn truyện thiếu địa chỉ" }
                    sources.add(source)
                }
            }
            else -> throw JsonSyntaxException("Dữ liệu phải là nguồn truyện hoặc danh sách nguồn")
        }
        checkActive()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Dữ liệu thừa sau JSON" }
        return BookSourceImportData(sources, urls)
    }
}
