package org.bepass.oblivion.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import java.util.Properties

/**
 * A [ValueSource] that reads an **optional** `.properties` file and exposes its
 * contents as a `Map<String, String>`.  When the file is absent or not set, an
 * empty map is returned.
 *
 * Typical usage in a Gradle build script:
 * ```kotlin
 * val keystoreProperties by providers.of(OptionalPropertiesValueSource::class) {
 *     parameters.file.set(rootProject.layout.projectDirectory.file("keystore.properties"))
 * }
 * // later: keystoreProperties.get()["storePassword"]
 * ```
 */
abstract class OptionalPropertiesValueSource :
    ValueSource<Map<String, String>, OptionalPropertiesValueSource.Params> {

    /**
     * Parameters for [OptionalPropertiesValueSource].
     */
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

        // تبدیل به LinkedHashMap برای حفظ ترتیب ورودی‌ها (اختیاری)
        return props.mapNotNull { (key, value) ->
            val k = key?.toString() ?: return@mapNotNull null
            val v = value?.toString() ?: return@mapNotNull null
            k to v
        }.toMap(LinkedHashMap())
    }
}