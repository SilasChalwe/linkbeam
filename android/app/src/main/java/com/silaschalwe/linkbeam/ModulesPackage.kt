package com.silaschalwe.linkbeam

import android.util.Log
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class ModulesPackage : ReactPackage {
    companion object {
        private const val TAG = "LinkBeamModulesPackage"
        
        /**
         * Get information about the modules provided by this package
         */
        fun getModuleInfo(): String {
            return "LinkBeam Modules Package v1.0 - Provides SimpleHttpServer and NetworkHelper modules"
        }

        /**
         * Get list of module names provided by this package
         */
        fun getProvidedModuleNames(): List<String> {
            return listOf("SimpleHttpServer", "NetworkHelper")
        }
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        val modules = mutableListOf<NativeModule>()

        try {
            // Add SimpleHttpServer module
            val httpServer = SimpleHttpServer(reactContext)
            modules.add(httpServer)
            Log.d(TAG, "SimpleHttpServer module added successfully")

            // Add NetworkHelper module
            val networkHelper = NetworkHelper(reactContext)
            modules.add(networkHelper)
            Log.d(TAG, "NetworkHelper module added successfully")

            Log.i(TAG, "Successfully created ${modules.size} native modules")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating native modules: ${e.message}", e)
            // Don't let the app crash - return empty list if modules fail to initialize
        }

        return modules
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        // No custom view managers in this package
        Log.d(TAG, "No view managers to create")
        return emptyList()
    }
}