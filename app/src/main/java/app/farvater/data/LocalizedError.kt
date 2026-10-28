package app.farvater.data

import android.content.Context
import androidx.annotation.StringRes
import java.io.IOException
import java.net.UnknownHostException

interface LocalizedError {
    val textId: Int
    val arguments: Array<out Any>
}

class LocalizedIOException(
    @param:StringRes override val textId: Int,
    override vararg val arguments: Any,
) : IOException("Farvater error $textId"), LocalizedError

class LocalizedHostException(
    @param:StringRes override val textId: Int,
    override vararg val arguments: Any,
) : UnknownHostException("Farvater DNS error $textId"), LocalizedError

class LocalizedStateException(
    @param:StringRes override val textId: Int,
    override vararg val arguments: Any,
) : IllegalStateException("Farvater state error $textId"), LocalizedError

fun Throwable.userMessage(context: Context): String = if (this is LocalizedError) {
    context.localizedString(textId, *arguments)
} else {
    message ?: javaClass.simpleName
}
