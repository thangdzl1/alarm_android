package com.example.simplealarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


class AlarmViewModelFactory(private val repository: AlarmRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AlarmModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}