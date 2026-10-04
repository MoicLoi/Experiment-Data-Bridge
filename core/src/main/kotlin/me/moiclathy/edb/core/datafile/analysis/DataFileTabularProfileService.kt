package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.application.DataFileApplicationService
import me.moiclathy.edb.core.datafile.format.DataFileReaderRegistry
import me.moiclathy.edb.core.datafile.format.TabularData

data class TabularColumnProfile(
    val name: String,
    val type: String,
    val values: List<String>
)

data class DataFileTabularProfile(val columns: List<TabularColumnProfile>)

class DataFileTabularProfileService(
    private val files: DataFileApplicationService,
    private val readers: DataFileReaderRegistry
) {
    fun profile(projectId: Int, dataFileId: Int, userId: Int): DataFileTabularProfile {
        val download = files.openDownload(projectId, dataFileId, userId)
        return download.input.use { input ->
            readers.require(download.dataFile.format, TabularData::class).read(input).use { table ->
                val samples = table.rows().take(MAX_ROWS).toList()
                DataFileTabularProfile(table.headers.mapIndexed { index, name ->
                    val values = samples.mapNotNull { it.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty) }
                    val numeric = values.isNotEmpty() && values.all { it.toDoubleOrNull() != null }
                    TabularColumnProfile(
                        name = name,
                        type = if (numeric) "NUMBER" else "STRING",
                        values = if (!numeric) values.distinct().take(MAX_VALUES) else emptyList()
                    )
                })
            }
        }
    }

    companion object { private const val MAX_ROWS = 500; private const val MAX_VALUES = 50 }
}
