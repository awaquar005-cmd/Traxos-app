package app.traxos.finance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.traxos.finance.TraxosApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MoneyToolsViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as TraxosApplication
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val completed = MutableStateFlow(0L)

    fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                block()
                completed.value += 1
                app.financeScheduler.kick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error.value = e.message ?: "Check the entered values"
            } catch (e: java.time.DateTimeException) {
                error.value = "Choose a valid date"
            } catch (e: Exception) {
                error.value = "Could not save. Existing records are kept; retry."
            } finally {
                busy.value = false
            }
        }
    }
}
