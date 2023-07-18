package com.github.whitescent.mastify.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import at.connyduck.calladapter.networkresult.fold
import com.github.whitescent.mastify.data.repository.AccountRepository
import com.github.whitescent.mastify.data.repository.PreferenceRepository
import com.github.whitescent.mastify.database.AppDatabase
import com.github.whitescent.mastify.network.MastodonApi
import com.github.whitescent.mastify.network.model.status.Status
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.Continue
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.End
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.Null
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.Start
import com.github.whitescent.mastify.paging.LoadState
import com.github.whitescent.mastify.paging.Paginator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
  preferenceRepository: PreferenceRepository,
  private val db: AppDatabase,
  private val accountRepository: AccountRepository,
  private val api: MastodonApi,
) : ViewModel() {

  private val timelineDao = db.timelineDao()
  private var unsortedTimelineList = mutableListOf<Status>()
  private var previousStatusList = mutableListOf<Status>()
  private var nextPage: String? = null

  val timelineScrollPosition = preferenceRepository.timelineModel?.firstVisibleItemIndex ?: 0
  val timelineScrollPositionOffset = preferenceRepository.timelineModel?.offset ?: 0

  val activeAccount get() = accountRepository.activeAccount!!
  var uiState by mutableStateOf(HomeUiState())
    private set

  private val paginator = Paginator(
    initialKey = nextPage,
    onLoadUpdated = { uiState = uiState.copy(timelineLoadState = it) },
    onRequest = { nextPage ->
      val response = api.homeTimeline(maxId = nextPage)
      if (response.isSuccessful && !response.body().isNullOrEmpty()) {
        val body = response.body()!!
        Result.success(body)
      } else {
        Result.success(emptyList())
      }
    },
    getNextKey = {
      it.lastOrNull()?.id
    },
    onError = {
      it?.printStackTrace()
    },
    onAppend = { items, newKey ->
      unsortedTimelineList = (unsortedTimelineList + items).toMutableList()
      uiState = uiState.copy(
        timeline = reorderedStatuses(unsortedTimelineList),
        endReached = items.isEmpty()
      )
      nextPage = newKey
      db.withTransaction {
        replaceStatusRange(unsortedTimelineList)
      }
    },
    onRefresh = { items ->
      if (unsortedTimelineList.isNotEmpty()) {
        val lastStatusInApi = items.last()
        if (!unsortedTimelineList.any { it.id == lastStatusInApi.id }) {
          // TODO Implementation mark loadMore
          uiState = uiState.copy(showLoadMorePlacerHolder = true)
          loadPreviousStatus()
        } else {
          val newStatusList = items.filterNot {
            unsortedTimelineList.any { saved -> saved.id == it.id }
          }
          // 添加 api 获取到的新的帖子
          val indexInSavedList = unsortedTimelineList.indexOfFirst { it.id == items.last().id } + 1
          val statusListAfterIndex =
            unsortedTimelineList.subList(indexInSavedList, unsortedTimelineList.size)
          unsortedTimelineList = (items + statusListAfterIndex).toMutableList()
          uiState = if (newStatusList.isNotEmpty())
            uiState.copy(
              timelineWithNewStatus = reorderedStatuses(unsortedTimelineList),
              newStatusCount = newStatusList.size,
              showNewStatusButton = newStatusList.isNotEmpty()
            )
          else {
            uiState.copy(
              timeline = reorderedStatuses(unsortedTimelineList),
              endReached = items.isEmpty()
            )
          }
        }
      } else {
        unsortedTimelineList = items.toMutableList()
        uiState = uiState.copy(
          timeline = reorderedStatuses(unsortedTimelineList),
          endReached = items.isEmpty()
        )
      }
      db.withTransaction {
        // replaceStatusRange(unsortedTimelineList)
        reinsertAllStatus(unsortedTimelineList, activeAccount.id)
      }
    }
  )

  init {
    viewModelScope.launch {
      unsortedTimelineList = timelineDao.getStatuses(activeAccount.id).toMutableList()
      uiState = uiState.copy(timeline = reorderedStatuses(unsortedTimelineList))
      paginator.refresh()
      uiState = uiState.copy(isInitialLoad = true)
      // fetch the latest account info
      api.accountVerifyCredentials(
        domain = activeAccount.domain,
        auth = "Bearer ${activeAccount.accessToken}"
      )
        .fold(
          {
            accountRepository.updateActiveAccount(it)
          },
          {
            it.printStackTrace()
          }
        )
    }
  }

  fun append() = viewModelScope.launch {
    paginator.append()
  }

  fun refreshTimeline() = viewModelScope.launch {
    paginator.refresh()
  }

  fun favoriteStatus(id: String) = viewModelScope.launch {
    api.favouriteStatus(id)
  }

  fun unfavoriteStatus(id: String) = viewModelScope.launch {
    api.unfavouriteStatus(id)
  }

  private fun loadPreviousStatus() {
    viewModelScope.launch {
      val response = api.homeTimeline(
        maxId = if (previousStatusList.isNotEmpty()) previousStatusList.last().id else null,
        limit = 20
      )
      if (response.isSuccessful && !response.body().isNullOrEmpty()) {
        val body = response.body()!!
        if (unsortedTimelineList.any { it.id == body.last().id }) {
          val startIndex = unsortedTimelineList.indexOf(body.last()) + 1
          val remainList = unsortedTimelineList.subList(startIndex, unsortedTimelineList.size)
          unsortedTimelineList = (previousStatusList + body + remainList).toMutableList()
          uiState = uiState.copy(
            timeline = reorderedStatuses(unsortedTimelineList),
            previousTimeline = emptyList(),
            showLoadMorePlacerHolder = false
          )
          db.withTransaction {
            // replaceStatusRange(unsortedTimelineList)
            reinsertAllStatus(unsortedTimelineList, activeAccount.id)
          }
        } else {
          if (previousStatusList.isNotEmpty()) previousStatusList += body
          else previousStatusList = body.toMutableList()
          uiState = uiState.copy(previousTimeline = reorderedStatuses(previousStatusList))
        }
      } else {
        // ERROR
      }
    }
  }

  fun dismissButton() {
    uiState = uiState.copy(
      timeline = uiState.timelineWithNewStatus,
      timelineWithNewStatus = emptyList(),
      showNewStatusButton = false
    )
  }

  private fun reorderedStatuses(statuses: List<Status>): List<Status> {
    if (statuses.isEmpty()) return emptyList()

    fun findReplyStatusById(id: String?) = id?.let {
      statuses.find { status -> status.id == it }
    }

    val id2index = hashMapOf<String, Boolean>()
    var reorderedStatuses = statuses.toMutableList()

    statuses.forEach { currentStatus ->
      if (currentStatus.isInReplyTo && id2index[currentStatus.id] == null) {
        val replyStatusList = ArrayDeque<Status>().apply { add(currentStatus) }
        var replyToStatus = currentStatus.inReplyToId

        // 获取回复链
        while (true) {
          val replyTo = findReplyStatusById(replyToStatus)
          if (replyTo != null) {
            replyStatusList.addFirst(replyTo)
            replyToStatus = replyTo.inReplyToId
          } else break
        }

        if (replyStatusList.size == 1) {
          // 如果为 1 时，则代表在当前的 timeline List 中找不到这个 id
          // 则添加 hasUnloadedInReplyTo 属性
          val unloadReplyStatusIndex =
            reorderedStatuses.indexOfFirst { it.id == replyStatusList.first().id }
          reorderedStatuses[unloadReplyStatusIndex] =
            reorderedStatuses[unloadReplyStatusIndex].copy(
              hasUnloadedReplyStatus = true,
              replyChainType = reorderedStatuses[unloadReplyStatusIndex].replyChainType
                .takeIf { it != Null } ?: End
            )
        } else {
          val finalReplyStatusList = ArrayDeque<Status>().apply {
            add(replyStatusList.first().copy(replyChainType = Start))
          }
          // 给组合完成的回复链更新指定的属性，并且标记不需要重复获取回复链的 status
          replyStatusList.forEachIndexed { replyIndex, status ->
            when (replyIndex) {
              in 1 until replyStatusList.lastIndex -> {
                finalReplyStatusList.add(
                  // 将回复数量大于等于 4 的帖子中，隐藏第一个到倒数第二个中间的帖子
                  // 并在倒数第二个帖子标记这是一个多回复链的帖子，方便 UI 层更新对应的 line
                  status.copy(
                    replyChainType = Continue,
                    hasMultiReplyStatus = replyStatusList.size >= 4 &&
                      replyIndex == replyStatusList.lastIndex - 1,
                    shouldShow = !(replyStatusList.size >= 4 && replyIndex < replyStatusList.size - 2)
                  )
                )
                id2index[status.id] = true
              }
              replyStatusList.lastIndex -> {
                finalReplyStatusList.add(status.copy(replyChainType = End))
              }
            }
          }
          // 删除原本的 status，并替换为获取到的回复链
          val tempList = reorderedStatuses.toMutableList()
          val startAt = reorderedStatuses.indexOfFirst { finalReplyStatusList.last().id == it.id }
          reorderedStatuses.forEachIndexed { reorderedIndex, status ->
            if (reorderedIndex >= startAt && finalReplyStatusList.any { replyList ->
                status.id == replyList.id
              }) {
              tempList.remove(status)
            }
          }
          reorderedStatuses = tempList
          reorderedStatuses.addAll(startAt, finalReplyStatusList)
        }
      }
    }
    return reorderedStatuses
  }

  private suspend fun reinsertAllStatus(statuses: List<Status>, accountId: Long) {
    timelineDao.clearAll(accountId)
    statuses.forEach { timelineDao.insert(it.toEntity(accountId)) }
  }

  private suspend fun replaceStatusRange(statuses: List<Status>) {
    if (statuses.isNotEmpty()) {
      timelineDao.deleteRange(activeAccount.id, statuses.last().id, statuses.first().id)
    }
    for (status in statuses) {
      timelineDao.insert(status.toEntity(activeAccount.id))
    }
  }
}

data class HomeUiState(
  val timeline: List<Status> = emptyList(),
  val timelineWithNewStatus: List<Status> = emptyList(),
  val previousTimeline: List<Status> = emptyList(),
  val newStatusCount: Int = 0,
  val showNewStatusButton: Boolean = false,
  val isInitialLoad: Boolean = false,
  val endReached: Boolean = false,
  val timelineLoadState: LoadState = LoadState.NotLoading,
  val showLoadMorePlacerHolder: Boolean = false
)
