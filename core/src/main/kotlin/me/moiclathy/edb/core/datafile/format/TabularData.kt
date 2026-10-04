package me.moiclathy.edb.core.datafile.format

import java.util.concurrent.atomic.AtomicBoolean

class TabularData(
    rawHeaders: List<String>,
    rows: Sequence<List<String>>,
    private val closeAction: () -> Unit = {}
) : AutoCloseable {
    val headers: List<String> = validateTabularHeaders(rawHeaders)
    private val consumed = AtomicBoolean(false)
    private val sourceRows = rows

    fun rows(): Sequence<List<String>> {
        check(consumed.compareAndSet(false, true)) { "Tabular rows may only be consumed once." }
        return sourceRows.mapIndexed { index, row ->
            require(row.size == headers.size) {
                "Tabular row ${index + 2} has ${row.size} columns; expected ${headers.size}."
            }
            row
        }
    }

    override fun close() = closeAction()
}

internal fun validateTabularHeaders(rawHeaders: List<String>): List<String> {
    require(rawHeaders.isNotEmpty()) { "Tabular input must contain a header row." }
    val headers = rawHeaders.mapIndexed { index, value ->
        value.removePrefix(if (index == 0) "\uFEFF" else "").trim()
    }
    require(headers.none { it.isBlank() }) { "Tabular headers must not be blank." }
    require(headers.distinct().size == headers.size) { "Tabular headers must be unique." }
    return headers
}
