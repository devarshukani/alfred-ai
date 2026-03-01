package com.alfredassistant.alfred_ai.db

import android.content.Context
import io.objectbox.BoxStore

/**
 * Singleton holder for the ObjectBox store.
 * Initialize once from Activity/Application, then access anywhere.
 */
object ObjectBoxStore {

    lateinit var store: BoxStore
        private set

    val isInitialized: Boolean get() = ::store.isInitialized

    fun init(context: Context) {
        if (isInitialized) return
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }
}
