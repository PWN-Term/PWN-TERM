package io.neoterm.frontend.component.helper

import io.neolang.visitor.ConfigVisitor
import io.neoterm.component.config.ConfigureComponent
import io.neoterm.frontend.component.ComponentManager
import io.neoterm.frontend.component.NeoComponent
import io.neoterm.frontend.logging.NLog
import java.io.File

/**
 * @author kiva
 */
abstract class ConfigFileBasedComponent<out T : ConfigFileBasedObject> : NeoComponent {
    companion object {
        private val TAG = ConfigFileBasedComponent::class.java.simpleName
    }

    open val checkComponentFileWhenObtained = false

    override fun onServiceInit() {
        onCheckComponentFiles()
    }

    override fun onServiceDestroy() {
    }

    override fun onServiceObtained() {
        if (checkComponentFileWhenObtained) {
            onCheckComponentFiles()
        }
    }

    fun loadConfigure(file: File): T? {
        return try {
            val loaderService = ComponentManager.getComponent<ConfigureComponent>()
            val configure = loaderService.newLoader(file).loadConfigure()
                    ?: throw RuntimeException("Parse configuration failed.")

            val configVisitor = configure.getVisitor()
            val componentObject = onCreateComponentObject(configVisitor)
            componentObject.onConfigLoaded(configVisitor)
            componentObject
        } catch (e: RuntimeException) {
            NLog.e(TAG, "Failed to load config: ${file.absolutePath}: ${e.localizedMessage}")
            null
        }
    }

    abstract fun onCheckComponentFiles()

    abstract fun onCreateComponentObject(configVisitor: ConfigVisitor): T
}