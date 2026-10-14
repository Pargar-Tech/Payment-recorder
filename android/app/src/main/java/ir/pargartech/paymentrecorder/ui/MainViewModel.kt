package ir.pargartech.paymentrecorder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.pargartech.paymentrecorder.core.date.JalaliMonth
import ir.pargartech.paymentrecorder.core.parser.TxType
import ir.pargartech.paymentrecorder.data.AccountEntity
import ir.pargartech.paymentrecorder.data.CategoryEntity
import ir.pargartech.paymentrecorder.data.CategoryRuleEntity
import ir.pargartech.paymentrecorder.data.Prefs
import ir.pargartech.paymentrecorder.data.Repo
import ir.pargartech.paymentrecorder.data.ReviewItemEntity
import ir.pargartech.paymentrecorder.data.ScanSummary
import ir.pargartech.paymentrecorder.data.Scope
import ir.pargartech.paymentrecorder.data.SenderTrust
import ir.pargartech.paymentrecorder.data.TxFilter
import ir.pargartech.paymentrecorder.data.TxRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val done: Int, val total: Int) : ScanState
    data class Finished(val summary: ScanSummary) : ScanState
    data class Failed(val message: String) : ScanState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repo: Repo, private val prefs: Prefs) : ViewModel() {

    private val _month = MutableStateFlow(JalaliMonth.current())
    val month: StateFlow<JalaliMonth> = _month.asStateFlow()

    private val _filter = MutableStateFlow(TxFilter())
    val filter: StateFlow<TxFilter> = _filter.asStateFlow()

    val rows: StateFlow<List<TxRow>> = combine(_month, _filter) { m, f -> m to f }
        .flatMapLatest { (m, f) -> repo.transactions(m.startMillis(), m.endMillis(), f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts = repo.accounts.state(emptyList<AccountEntity>())
    val categories = repo.categories.state(emptyList<CategoryEntity>())
    val rules = repo.rules.state(emptyList<CategoryRuleEntity>())
    val reviews = repo.pendingReviews.state(emptyList<ReviewItemEntity>())
    val reviewCount = repo.pendingReviewCount.state(0)

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    val initialScanDone: Boolean get() = prefs.initialScanDone

    fun transaction(id: String): Flow<TxRow?> = repo.transaction(id)

    fun previousMonth() = _month.update { it.previous() }
    fun nextMonth() = _month.update { it.next() }
    fun setFilter(f: TxFilter) {
        _filter.value = f
    }

    /** [monthsBack] null = whole inbox. */
    fun scanInbox(monthsBack: Int?) {
        if (_scan.value is ScanState.Running) return
        val since = monthsBack?.let { ZonedDateTime.now().minusMonths(it.toLong()).toInstant().toEpochMilli() } ?: 0L
        _scan.value = ScanState.Running(0, 0)
        viewModelScope.launch {
            _scan.value = try {
                val summary = repo.scanInbox(since) { done, total -> _scan.value = ScanState.Running(done, total) }
                prefs.initialScanDone = true
                ScanState.Finished(summary)
            } catch (e: SecurityException) {
                ScanState.Failed("مجوز خواندن پیامک داده نشده است")
            } catch (e: Exception) {
                ScanState.Failed(e.message ?: "خطا در اسکن")
            }
        }
    }

    fun dismissScanResult() {
        _scan.value = ScanState.Idle
    }

    fun updateTransaction(id: String, note: String, payer: String, categoryId: String?, hidden: Boolean) =
        launch { repo.updateTransaction(id, note, payer, categoryId, hidden) }

    fun updateAccount(id: String, name: String, scope: Scope) = launch { repo.updateAccount(id, name, scope) }

    fun addCategory(name: String) = launch { repo.addCategory(name) }

    fun addRule(categoryId: String, contains: String?, accountId: String?, type: TxType?) =
        launch { repo.addRule(categoryId, contains, accountId, type) }

    fun deleteRule(id: String) = launch { repo.deleteRule(id) }

    fun resolveReview(item: ReviewItemEntity, type: TxType, amountRial: Long, accountId: String?, trust: Boolean) =
        launch { repo.resolveReview(item, type, amountRial, accountId, trust) }

    fun dismissReview(item: ReviewItemEntity) = launch { repo.dismissReview(item) }

    fun setSenderTrust(sender: String, trust: SenderTrust) = launch { repo.setSenderTrust(sender, trust) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private fun <T> MutableStateFlow<T>.update(f: (T) -> T) {
        value = f(value)
    }
}
