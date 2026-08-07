package com.github.op88.smartcopy.ocr

import com.google.mlkit.vision.text.Text
import org.junit.Test
import java.lang.reflect.Constructor

class MockkWorkaroundTest {
    @Test
    fun test() {
        val constructors = Text.Element::class.java.declaredConstructors
        for (c in constructors) {
            println(c)
        }
    }
}
