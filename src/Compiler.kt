package fr.rhaz.kotlin

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.Modality
import javafx.stage.Stage
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngine
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import org.jetbrains.kotlin.script.jsr223.KotlinStandardJsr223ScriptTemplate
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.PrintStream
import java.nio.file.Files
import javax.script.Bindings
import javax.script.ScriptContext
import kotlin.streams.toList

fun main(args: Array<String>) = Application.launch(KotlinCompiler::class.java, *args)

class KotlinCompiler : Application() {

    override fun start(stage: Stage) {
        setIdeaIoUseFallback()
        window = stage
        window.apply(Window).apply { show() }
    }

    lateinit var window: Stage;

    val icon
        get() = KotlinCompiler::class.java.classLoader.getResourceAsStream("icon.png")

    val stylesheet
        get() = KotlinCompiler::class.java.classLoader.getResource("style.css").toExternalForm()

    val Window: Stage.() -> Unit = {
        icons.add(Image(icon))
        title = "Kotlin Compiler"
        isAlwaysOnTop = true
        minWidth = 400.0
        minHeight = 200.0
        isResizable = false
        scene()
    }

    val scene: Stage.() -> Unit = {
        scene = Scene(StackPane().apply(Content), minWidth, minHeight)
    }

    var status = Label();
    var hint = Label();

    val chooser = FileChooser().apply {
        ExtensionFilter("All Kotlin Files", "*.kt", "*.kts").also{extensionFilters.add(it)}
    }

    val Content: StackPane.() -> Unit = {

        status.apply {
            text = "Choose a file"
            font = Font.font(30.0);
            translateY = -20.0
        }.also { children.add(it); }

        hint.apply {
            text = "or drag and drop"
            translateY = 10.0
        }.also { children.add(it); }

        setOnMouseClicked{chooser.showOpenDialog(window)?.let{open(it)} }
        setOnDragOver { it.acceptTransferModes(*TransferMode.ANY); }
        setOnDragDropped here@{
            val drag = it.dragboard
            if(!drag.hasFiles()) return@here
            open(drag.files.singleOrNull() ?: return@here)
        }
    }

    fun engine() = KotlinJsr223JvmLocalScriptEngine(
        KotlinJsr223JvmLocalScriptEngineFactory(),
        classPath ?: File("libs").listFiles().toList(),
        KotlinStandardJsr223ScriptTemplate::class.qualifiedName!!,
        { ctx, types -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), types ?: emptyArray()) },
        arrayOf(Bindings::class)
    )

    val classPath = System.getProperty("kotlin.script.classpath")
        ?.split(File.pathSeparator)?.map(::File)
        ?.toMutableList()?.apply{addAll(File("libs").listFiles())}

    var run = Button("")
    var toclass = Button("")

    val processing: StackPane.(() -> Unit) -> Unit = {

        children.remove(run)
        children.remove(toclass)
        status.text = "Processing..."
        hint.text = "please wait"
        children.add(hint)

        this.apply {
            setOnMouseClicked {}
            setOnDragOver {}
        }

        val process = async(it)

        Button("X").apply {
            style = "-fx-background-color: transparent";
            minWidth = 40.0
            minHeight = 40.0
            translateX = 180.0
            translateY = -80.0
            setOnAction { process.interrupt(); window.scene() }
        }.also { children.add(it) }
    }

    val dialog: StackPane.(title: String, msg: String) -> Unit = { title, msg ->

        val dialog = Stage()

        val vbox = VBox().apply {
            alignment = Pos.CENTER
            padding = Insets(15.0)

            Label(msg).also {children.add(it)}

            Button("Close").apply {
                style = "-fx-background-color: white";
                setOnAction { dialog.close() }
            }.also { children.add(it) }
        }

        dialog.apply {
            icons.add(Image(icon))
            this.title = title
            isAlwaysOnTop = true
            initModality(Modality.WINDOW_MODAL)
            scene = Scene(vbox)
            show()
        }
    }

    val open: StackPane.(File) -> Unit = content@{ file ->
        val types = listOf(".kt", ".kts")
        if(types.none{file.name.endsWith(it)}) return@content
        status.text = file.name
        children.remove(hint)
        children.remove(run)
        children.remove(toclass)

        run = Button("Run").apply {
            translateY = 20.0
            style = "-fx-background-color: white";
            setOnAction {
                processing {
                    engine().apply {

                        val old = System.out
                        val baos = ByteArrayOutputStream()
                        System.setOut(PrintStream(baos))

                        val (result, status) = try{
                            eval(FileReader(file))
                            listOf(baos.toString(), "Done!")
                        } catch (ex: Exception) {
                            listOf(ex.localizedMessage, "Error!")
                        }

                        System.setOut(old);

                        Platform.runLater {
                            this@KotlinCompiler.status.text = status
                            children.remove(hint)
                            dialog("Result of " + file.name, result)
                        }
                    }
                }
            }
        }

        toclass = Button("Compile to class").apply {
            translateY = 20.0
            style = "-fx-background-color: white";
            setOnAction {

                processing {

                    val out = File(file.parentFile, file.nameWithoutExtension).apply {if(!exists()) mkdir()}
                    val log = File(out, "log.txt").apply {if(!exists()) createNewFile()}
                    val stream = PrintStream(log)
                    val msgs = PrintingMessageCollector(stream, MessageRenderer.WITHOUT_PATHS, true)

                    val libs = File("./libs").listFiles().map { it.path }

                    val result = K2JVMCompiler().run {
                        val args = K2JVMCompilerArguments().apply {
                            freeArgs = listOf(file.absolutePath)
                            destination = out.absolutePath
                            classpath = System.getProperty("java.class.path")
                                        .split(System.getProperty("path.separator"))
                                        .filter{File(it).exists() && File(it).canRead()}
                                        .toMutableList().apply{addAll(libs)}
                                        .joinToString(System.getProperty("path.separator")).also { println(it) }
                            noStdlib = true
                            noReflect = true
                            skipRuntimeVersionCheck = true
                            reportPerf = true
                        }
                        execImpl(msgs, Services.EMPTY, args)
                    }.code

                    stream.close();

                    Platform.runLater {

                        status.text = when (result) {
                            0 -> "Done!"
                            else -> "Error!".also {
                                children.remove(hint)
                                Button("Show logs").apply {
                                    style = "-fx-background-color: white";
                                    translateY = 20.0
                                    setOnAction {
                                        Files.lines(log.toPath()).apply {
                                            dialog("Error while compiling " + file.name, toList().joinToString("\n"))
                                            close()
                                        }
                                    }
                                }.also { children.add(it) }
                            }
                        }

                        hint.text = "drag me out!"

                        this@content.setOnDragDetected {
                            val d = this@content.startDragAndDrop(*TransferMode.ANY)
                            val content = ClipboardContent().apply {
                                putFiles(out.listFiles { _, name -> name.endsWith(".class") }.toMutableList())
                            }.also { d.setContent(it) }
                        }
                    }

                }
            }
        }

        Platform.runLater {
            if (file.name.endsWith(".kts"))
                children.add(run);
            else
                children.add(toclass)
        }
    }


}

fun async(runnable: () -> Unit) = Thread(runnable).apply{start()}

fun async(timeout: Int, runnable: () -> Unit, callback: () -> Unit) = Thread(runnable).apply { tasker(timeout, callback) }

fun Thread.tasker(timeout: Int, callback: () -> Unit) = {
    start()
    val start = System.currentTimeMillis()
    while (this.isAlive) {
        Thread.sleep(1000)
        if (System.currentTimeMillis() - start > (timeout * 1000))
            interrupt()
    }
    Platform.runLater(callback)
}.let { Thread(it).start() }

fun wait(timeout: Long, callback: () -> Unit) = {
    Thread.sleep(timeout)
    Platform.runLater { callback() }
}.let { Thread(it).start() }
