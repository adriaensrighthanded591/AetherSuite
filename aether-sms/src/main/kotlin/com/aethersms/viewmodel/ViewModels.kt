package com.aethersms.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aethersms.data.model.Conversation
import com.aethersms.data.model.Message
import com.aethersms.data.repository.MmsSender
import com.aethersms.data.repository.SmsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── ConversationListViewModel ───────────────────────────────────────────────

class ConversationListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SmsRepository(app)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val filtered: StateFlow<List<Conversation>> = combine(_conversations, _search) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.displayName.contains(q, ignoreCase = true) ||
            it.snippet.contains(q, ignoreCase = true) ||
            it.recipientAddresses.any { a -> a.contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { loadConversations() }

    fun loadConversations() {
        viewModelScope.launch {
            _loading.value = true
            _conversations.value = repo.loadConversations()
            _loading.value = false
        }
    }

    fun setSearch(q: String) { _search.value = q }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch {
            repo.deleteConversation(threadId)
            loadConversations()
        }
    }
}

// ─── ConversationViewModel ───────────────────────────────────────────────────

class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val repo      = SmsRepository(app)
    private val mmsSender = MmsSender(app)

    private val _messages  = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Brouillon auto-sauvegardé ─────────────────────────────────────────
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    // ── Recherche dans la conversation ────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Message>> = combine(_messages, _searchQuery) { msgs, q ->
        if (q.isBlank()) emptyList()
        else msgs.filter { it.body.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── État interne ───────────────────────────────────────────────────────
    var threadId: Long  = -1L; private set
    var address:  String = ""; private set
    private var initialized = false
    private var draftSaveJob: Job? = null

    /**
     * Idempotent : si appelé avec les mêmes arguments, ne recharge pas.
     * Utiliser depuis LaunchedEffect(threadId, address).
     */
    fun init(threadId: Long, address: String) {
        if (initialized && this.threadId == threadId && this.address == address) return
        this.threadId   = threadId
        this.address    = address
        this.initialized = true
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _loading.value = true
            _messages.value = repo.loadMessages(threadId)
            _loading.value = false
        }
    }

    fun sendMessage(body: String, attachments: List<Uri> = emptyList()) {
        viewModelScope.launch {
            _sending.value = true
            try {
                if (attachments.isEmpty()) {
                    repo.sendSms(address, body)
                } else {
                    mmsSender.sendMms(listOf(address), body, attachments)
                }
                clearDraft()
                // Petit délai pour laisser le ContentProvider indexer le message envoyé
                delay(300)
                loadMessages()
            } catch (e: Exception) {
                _error.value = "Échec d'envoi : ${e.localizedMessage ?: "erreur inconnue"}"
            } finally {
                _sending.value = false
            }
        }
    }

    /**
     * Auto-sauvegarde du brouillon avec debounce 500 ms.
     * L'UI appelle ceci à chaque frappe.
     */
    fun onDraftChanged(text: String) {
        _draft.value = text
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(500)
            // Persister dans le ContentProvider (type = MESSAGE_TYPE_DRAFT)
            if (threadId > 0) repo.saveDraft(threadId, address, text)
        }
    }

    fun clearDraft() {
        draftSaveJob?.cancel()
        _draft.value = ""
        if (threadId > 0) {
            viewModelScope.launch { repo.deleteDraft(threadId) }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearSearch()             { _searchQuery.value = "" }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            repo.deleteMessage(message)
            loadMessages()
        }
    }

    fun clearError() { _error.value = null }
}

// ─── ContactSearchViewModel ──────────────────────────────────────────────────

class ContactSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SmsRepository(app)

    private val _results = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val results: StateFlow<List<Pair<String, String>>> = _results.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            _results.value = if (query.isBlank()) emptyList()
                             else repo.searchContacts(query)
        }
    }
}
