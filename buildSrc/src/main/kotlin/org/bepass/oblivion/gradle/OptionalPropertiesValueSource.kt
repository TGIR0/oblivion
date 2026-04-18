package org.bepass.oblivion.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import java.util.Properties

abstract class OptionalPropertiesValueSource :
    ValueSource<Map<String, String>, OptionalPropertiesValueSource.Params> {
    interface Params : ValueSourceParameters {
        @get:InputFile
        @get:Optional
        val file: RegularFileProperty
    }

    override fun obtain(): Map<String, String> {
        val propsFile = parameters.file.orNull?.asFile ?: return emptyMap()
        if (!propsFile.isFile) return emptyMap()

        val props = Properties()
        propsFile.inputStream().use { props.load(it) }

        val result = LinkedHashMap<String, String>(props.size)
        for ((key, value) in props) {
            val k = key?.toString() ?: continue
            val v = value?.toString() ?: continue
            result[k] = v
        }
        return result
    }
}
