package com.pineapple.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.pineapple.app.paging.RequestResult
import com.pineapple.app.model.reddit.PostItem
import com.pineapple.app.model.reddit.SubredditItem
import com.pineapple.app.model.reddit.UserAbout
import com.pineapple.app.model.reddit.UserItem
import com.pineapple.app.network.NetworkServiceBuilder.REDDIT_BASE_URL
import com.pineapple.app.network.NetworkServiceBuilder.apiService
import com.pineapple.app.network.RedditNetworkService

class SearchViewModel : ViewModel() {

    private val networkService by lazy { apiService<RedditNetworkService>(REDDIT_BASE_URL) }

    var currentSearchQuery by mutableStateOf(TextFieldValue())
    var currentSearchFilter by mutableStateOf(0)
    var currentPostList = mutableStateListOf<PostItem>()
    var currentSubredditList = mutableStateListOf<SubredditItem>()
    var currentUserList = mutableStateListOf<UserItem>()

    var topSubredditList = mutableStateListOf<SubredditItem>()
    var topUserList = mutableStateListOf<UserItem>()

    suspend fun requestSubreddits() {
        val communities = networkService.fetchTopSubreddits()
        val users = networkService.fetchTopUsers()
        topSubredditList.apply {
            clear()
            repeat(5) { add(communities.data.children[it]) }
        }
        topUserList.apply {
            clear()
            repeat(5) { add(users.data.children[it]) }
        }
    }

    suspend fun updateSearchResults() {
        if (currentSearchQuery.text.length > 2) {
            networkService.apply {
                when (currentSearchFilter) {
                    0 -> {
                        searchPosts(currentSearchQuery.text).data.children
                            .processToList(currentPostList)
                        searchCommunities(currentSearchQuery.text).data.children
                            .processToList(currentSubredditList)
                        searchUsers(currentSearchQuery.text).data.children
                            .processToList(currentUserList)
                    }
                    1 -> {
                        searchPosts(currentSearchQuery.text).data.children
                            .processToList(currentPostList)
                    }
                    2 -> {
                        searchCommunities(currentSearchQuery.text).data.children
                            .processToList(currentSubredditList)
                    }
                    3 -> {
                        searchUsers(currentSearchQuery.text).data.children
                            .processToList(currentUserList)
                    }
                }
            }
        }
    }

    private fun <T> List<T>.processToList(list: SnapshotStateList<T>) {
        val elements = this
        list.apply {
            clear()
            addAll(elements)
        }
    }

}