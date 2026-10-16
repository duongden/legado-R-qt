package io.legado.app.ui.association

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SvgOptionLabelsTest {
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg"><text>选项</text><text>
        <tspan>0. 古代言情</tspan><tspan>18. 未来幻纯</tspan></text></svg>"""

    @Test fun extractsTextOnceInOrderFromBase64AndPercentEncodedSvg() {
        val expected = listOf("选项", "0. 古代言情", "18. 未来幻纯")
        assertEquals(expected, SvgOptionLabels.parse("data:image/svg+xml;base64," +
            Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))))
        assertEquals(expected, SvgOptionLabels.parse("data:image/svg+xml," +
            java.net.URLEncoder.encode(svg, "UTF-8").replace("+", "%20")))
    }

    @Test fun leavesOrdinaryVerificationImagesAndMalformedDataAlone() {
        listOf("https://example.com/code.svg", "data:image/png;base64,AAAA",
            "data:image/svg+xml;base64,???", "data:image/svg+xml,<svg><text>1234</text></svg>")
            .forEach { assertNull(SvgOptionLabels.parse(it)) }
    }

    @Test fun translatesOnlyLabelsAndPreservesSelectionNumbers() = runBlocking {
        assertEquals("18. Đam mỹ tương lai kỳ ảo", SvgOptionLabels.translate("18. 未来幻纯") {
            assertEquals("未来幻纯", it)
            "Đam mỹ tương lai kỳ ảo"
        })
    }
}
