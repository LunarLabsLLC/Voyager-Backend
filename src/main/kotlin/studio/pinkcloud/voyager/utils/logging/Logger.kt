package studio.pinkcloud.voyager.utils.logging

import studio.pinkcloud.voyager.utils.AnsiColor

object LoggerSettings {
    var saveToFile = true
    var saveDirectoryPath = "./logs/"
    var loggerStyle = LoggerStyle.PREFIX
    var logFileNameFormat = "yyyy-MM-dd-Hms"
}

enum class LoggerStyle(val pattern: String) {
    FULL("<background><black><prefix>: <message>"),
    PREFIX("<background><black><prefix>:<reset> <foreground><message>"),
    SUFFIX("<foreground><prefix>: <background><black><message>"),
    TEXT_ONLY("<foreground><prefix>: <message>"),
}

fun log(
    message: String,
    type: CustomLogType = LogType.RUNTIME,
) {
    var pattern = LoggerSettings.loggerStyle.pattern
    pattern = pattern.replace("<background>", type.colorPair.background.code)
    pattern = pattern.replace("<foreground>", type.colorPair.foreground.code)
    pattern = pattern.replace("<black>", AnsiColor.BLACK.code)
    pattern = pattern.replace("<prefix>", type.name)
    pattern = pattern.replace("<message>", message)
    pattern = pattern.replace("<reset>", AnsiColor.RESET.code)

    println("$pattern${AnsiColor.RESET}")
    if (LoggerSettings.saveToFile) LoggerFileWriter.writeToFile(message, type)
}

fun log(exception: Exception) {
    log("$exception", LogType.EXCEPTION)
    exception.stackTrace.forEach {
        log("   $it", LogType.EXCEPTION)
    }
}

fun log(exception: Throwable) {
    log("$exception", LogType.EXCEPTION)
    exception.stackTrace.forEach {
        log("   $it", LogType.EXCEPTION)
    }
}

fun logAndThrow(exception: Exception) {
    log(exception)
    throw exception
}

fun logAndThrow(message: String) {
    logAndThrow(Exception(message))
}
