package io.legado.app

import com.script.rhino.RhinoScriptEngine
import com.script.ScriptBindings
import org.mozilla.javascript.Scriptable
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RhinoConcurrentJsonTest {
    @Test fun sourceScopesCanInitializeJsonOnTheSamePrototypeConcurrently() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            repeat(20) {
                val prototype = RhinoScriptEngine.getRuntimeScope(ScriptBindings()).prototype
                val start = CountDownLatch(1)
                val futures = (0 until 8).map { worker ->
                    executor.submit<Unit> {
                        val bindings = ScriptBindings().apply {
                            this.prototype = prototype
                            put("marker", "$worker")
                        }
                        check(start.await(10, TimeUnit.SECONDS))
                        repeat(10) {
                            assertEquals("$worker", RhinoScriptEngine.eval(
                                "JSON.parse(JSON.stringify({value:marker})).value", bindings as Scriptable
                            ))
                        }
                    }
                }
                start.countDown()
                futures.forEach { it.get(30, TimeUnit.SECONDS) }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun independentScopesInitializeAndUseJsonConcurrently() {
        val workers = 8
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val futures = (0 until workers).map { worker ->
                executor.submit<Unit> {
                    check(start.await(10, TimeUnit.SECONDS))
                    repeat(100) { iteration ->
                        val marker = "$worker:$iteration"
                        assertEquals(marker, RhinoScriptEngine.eval(
                            "var item=JSON.parse(JSON.stringify({value:marker,arr:[null,true,3]}));" +
                                "if(item.arr.length!==3) throw Error('array'); item.value"
                        ) { put("marker", marker) })
                    }
                }
            }
            start.countDown()
            futures.forEach { it.get(45, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
