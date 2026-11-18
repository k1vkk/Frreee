package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
import com.example.data.local.ConnectionHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as NexoraApp).database.connectionHistoryDao()

    val history: StateFlow<List<ConnectionHistoryEntity>> = dao.observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }
}
