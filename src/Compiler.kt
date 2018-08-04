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
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.Modality
import javafx.stage.Stage
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.streams.toList


fun main(args: Array<String>) {
    Application.launch(KotlinCompiler::class.java, *args)
}

class KotlinCompiler : Application() {

    override fun start(stage: Stage) {
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
        minWidth = 400.0
        minHeight = 200.0
        isResizable = false
        scene()
    }

    val scene: Stage.() -> Unit = {
        scene = Scene(StackPane().apply(Content), minWidth, minHeight)
    }

    val Top: HBox by lazy {
        HBox().apply {

            background = Background(BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY))

            Label("Kotlin Compiler").apply {
                padding = Insets(16.0, 16.0, 16.0, 16.0)
            }.also { children.add(it); }

            var x = 0 ; var y = 0
            setOnMousePressed {
                x = it.sceneX.toInt();
                y = it.sceneY.toInt();
            }
            setOnMouseDragged {
                window.x = it.screenX - x
                window.y = it.screenY - y
            }

            Button("—").apply {
                style = "-fx-background-color: transparent";
                translateX = 400.0
                padding = Insets(16.0, 0.0, 16.0, 0.0)
                setOnAction { window.isIconified = true }
            }.also { children.add(it); }

            Button("X").apply {
                style = "-fx-background-color: transparent";
                translateX = 420.0
                padding = Insets(16.0, 0.0, 16.0, 0.0)
                setOnAction { System.exit(0) }
            }.also { children.add(it); }
        }
    }

    lateinit var status: Label;
    lateinit var hint: Label;
    val Content: StackPane.() -> Unit = {

        val chooser = FileChooser().apply {
            ExtensionFilter("All Kotlin Files", "*.kt", "*.kts").also{extensionFilters.add(it)}
        }

        status = Label("Choose a file").apply {
            font = Font.font(30.0);
            translateY = -20.0
        }.also { children.add(it); }

        hint = Label("or drag and drop")
                .apply { translateY = 10.0 }
                .also { children.add(it); }

        setOnMouseClicked{ chooser.showOpenDialog(window)?.let{open(it)} }
        setOnDragOver { it.acceptTransferModes(*TransferMode.ANY); }
        setOnDragDropped here@{
            val drag = it.dragboard
            if(!drag.hasFiles()) return@here
            open(drag.files.singleOrNull() ?: return@here)
        }
    }

    val open: StackPane.(File) -> Unit = content@{ file ->
        val types = listOf("kt", "kts")
        if(types.none{file.name.endsWith(it)}) return@content
        status.text = file.name
        children.remove(hint)

        val toclass = Button("Compile to class").apply {
            translateY = 20.0
            style = "-fx-background-color: white";
            setOnAction {

                children.remove(this)
                status.text = "Processing..."
                hint.text = "please wait"
                children.add(hint)

                this@content.apply {
                    setOnMouseClicked {}
                    setOnDragOver {}
                }

                Button("X").apply {
                    style = "-fx-background-color: transparent";
                    minWidth = 40.0
                    minHeight = 40.0
                    translateX = 180.0
                    translateY = -80.0
                    setOnAction { window.scene() }
                }.also { children.add(it) }

                wait(500) {

                    val out = File(file.parentFile, file.nameWithoutExtension).apply {if(!exists()) mkdir()}
                    val log = File(out, "log.txt").apply {if(!exists()) createNewFile()}
                    val stream = PrintStream(log)
                    val msgs = PrintingMessageCollector(stream, MessageRenderer.WITHOUT_PATHS, true)

                    val result = K2JVMCompiler().run {
                        val args = K2JVMCompilerArguments().apply {
                            freeArgs = listOf(file.absolutePath)
                            destination = out.absolutePath
                            classpath = System.getProperty("java.class.path")
                                        .split(System.getProperty("path.separator"))
                                        .filter{File(it).exists() && File(it).canRead()}
                                        .joinToString(":")
                            noStdlib = true
                            noReflect = true
                            skipRuntimeVersionCheck = true
                            reportPerf = true
                        }
                        execImpl(msgs, Services.EMPTY, args)
                    }.code

                    stream.close();

                    status.text = when(result){
                        0 -> "Done!"
                        else -> "Error!".also {
                            children.remove(hint)
                            Button("Show logs").apply {
                                style = "-fx-background-color: white";
                                translateY = 20.0
                                setOnAction {

                                    val dialog = Stage()

                                    val vbox = VBox().apply {
                                        alignment = Pos.CENTER
                                        padding = Insets(15.0)
                                        Files.lines(log.toPath()).apply {
                                            toList().joinToString("\n").also{children.add(Label(it)) }
                                            close()
                                        }
                                        Button("Close").apply {
                                            style = "-fx-background-color: white";
                                            setOnAction { dialog.close() }
                                        }.also { children.add(it) }
                                    }

                                    dialog.apply {
                                        icons.add(Image(icon))
                                        title = "Error while compiling "+file.name
                                        initModality(Modality.WINDOW_MODAL)
                                        scene = Scene(vbox)
                                        show()
                                    }

                                }
                            }.also {children.add(it)}
                        }
                    }

                    hint.text = "drag me out!"

                    this@content.setOnDragDetected {
                        val d = this@content.startDragAndDrop(*TransferMode.ANY)
                        val content = ClipboardContent().apply {
                            putFiles(out.listFiles { _, name ->  name.endsWith(".class") }.toMutableList())
                        }.also { d.setContent(it) }
                    }

                }
            }
        }.also { children.add(it); }
    }


}

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
