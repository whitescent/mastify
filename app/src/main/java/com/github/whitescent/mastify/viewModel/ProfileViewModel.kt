package com.github.whitescent.mastify.viewModel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.github.whitescent.mastify.data.repository.AccountRepository
import com.github.whitescent.mastify.domain.StatusActionHandler
import com.github.whitescent.mastify.network.MastodonApi
import com.github.whitescent.mastify.network.model.account.Account
import com.github.whitescent.mastify.paging.ProfilePagingSource
import com.github.whitescent.mastify.screen.navArgs
import com.github.whitescent.mastify.screen.profile.ProfileNavArgs
import com.github.whitescent.mastify.utils.StatusAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val profilePagerSize = 20

@HiltViewModel
class ProfileViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  accountRepository: AccountRepository,
  private val statusActionHandler: StatusActionHandler,
  private val api: MastodonApi
) : ViewModel() {

  private val navArgs: ProfileNavArgs = savedStateHandle.navArgs()
  val snackBarFlow = statusActionHandler.snackBarFlow

  var uiState by mutableStateOf(
    ProfileUiState(
      account = navArgs.account,
      isSelf = navArgs.account.id == accountRepository.activeAccount!!.accountId
    )
  )
    private set

  val statusPager = Pager(
    config = PagingConfig(
      pageSize = profilePagerSize,
      enablePlaceholders = false,
    ),
    pagingSourceFactory = {
      ProfilePagingSource(
        onlyMedia = false,
        excludeReplies = true,
        api = api,
        accountId = uiState.account.id
      )
    },
  ).flow.cachedIn(viewModelScope)

  val statusWithReplyPager = Pager(
    config = PagingConfig(
      pageSize = profilePagerSize,
      enablePlaceholders = false,
    ),
    pagingSourceFactory = {
      ProfilePagingSource(excludeReplies = false, api = api, accountId = uiState.account.id)
    },
  ).flow.cachedIn(viewModelScope)

  val statusWithMediaPager = Pager(
    config = PagingConfig(
      pageSize = profilePagerSize,
      enablePlaceholders = false,
    ),
    pagingSourceFactory = {
      ProfilePagingSource(onlyMedia = true, api = api, accountId = uiState.account.id)
    },
  ).flow.cachedIn(viewModelScope)

  init {
    viewModelScope.launch {
      getRelationship(navArgs.account.id)
      fetchAccount(navArgs.account.id)
    }
  }

  fun onStatusAction(action: StatusAction, context: Context) = viewModelScope.launch {
    statusActionHandler.onStatusAction(action, context)
  }

  private suspend fun fetchAccount(accountId: String) {
    api.account(accountId).fold(
      {
        uiState = uiState.copy(account = it)
      },
      {
        it.printStackTrace()
      }
    )
  }

  private suspend fun getRelationship(accountId: String) {
    api.relationships(listOf(accountId)).fold(
      {
        uiState = uiState.copy(isFollowing = it.first().following)
      },
      {
        it.printStackTrace()
      }
    )
  }
}

data class ProfileUiState(
  val account: Account,
  val isSelf: Boolean,
  val isFollowing: Boolean? = null,
)
