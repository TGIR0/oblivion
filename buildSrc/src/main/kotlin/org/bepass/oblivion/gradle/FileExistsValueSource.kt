package org.bepass.oblivion.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional

abstract class FileExistsValueSource : ValueSource<Boolean, FileExistsValueSource.Params> {
    interface Params : ValueSourceParameters {
        @get:InputFile
        @get:Optional
        val file: RegularFileProperty
    }

    override fun obtain(): Boolean {
        return parameters.file.orNull?.asFile?.exists() == true
    }
}
