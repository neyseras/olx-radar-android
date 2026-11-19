package ua.magerko.olxradar.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ua.magerko.olxradar.data.local.AppDatabase
import ua.magerko.olxradar.data.network.ChromiumBridge
import ua.magerko.olxradar.data.network.OlxSearchClient

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext as Application
        val db = AppDatabase.getInstance(appContext)

        return when {
            modelClass.isAssignableFrom(FindingsViewModel::class.java) -> {
                FindingsViewModel(db.findingDao(), db.watchDao()) as T
            }
            modelClass.isAssignableFrom(WatchesViewModel::class.java) -> {
                val chromiumBridge = ChromiumBridge(appContext)
                val searchService = OlxSearchClient(chromiumBridge)
                WatchesViewModel(appContext, db.watchDao(), db.findingDao(), searchService) as T
            }
            modelClass.isAssignableFrom(StatusViewModel::class.java) -> {
                StatusViewModel(appContext, db.watchDao(), db.findingDao()) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
