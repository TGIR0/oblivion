package org.bepass.oblivion.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional

/**
 * A [ValueSource] that resolves to `true` when the configured [file][Params.file] exists,
 * and `false` otherwise.
 *
 * Typical usage inside a `gradle.build.kts`:
 * ```kotlin
 * val configExists by providers.of(FileExistsValueSource::class) {
 *     parameters.file.set(layout.projectDirectory.file("google-services.json"))
 * }
 * ```
 */
abstract class FileExistsValueSource : ValueSource<Boolean, FileExistsValueSource.Params> {

    /**
     * Parameters for [FileExistsValueSource].
     */
    interface Params : ValueSourceParameters {
        @get:InputFile
        @get:Optional
        val file: RegularFileProperty
    }

    override fun obtain(): Boolean =
        parameters.file.orNull?.asFile?.exists() == true
}
