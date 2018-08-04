/*
You can load this class from Java/Kotlin with:

import java.io.File
import java.net.URLClassLoader

fun load(file: File): Any? {
    try {
        val urls = arrayOf(file.parentFile.toURI().toURL())
        val loader = URLClassLoader(urls)
        val cls = loader.loadClass(file.nameWithoutExtension)
        return cls.newInstance()
    }catch (ex: Exception){
        ex.printStackTrace();
        return null
    }
}

 */

public class Test {
    init { println("It works!") }
}