package com.pineapple.app.components

import android.net.wifi.rtt.CivicLocationKeys.STATE
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.estimateAnimationDurationMillis
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key.Companion.F
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter.State.Empty.painter
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.pineapple.app.R
import com.pineapple.app.model.gfycat.GfycatObject
import com.pineapple.app.model.reddit.PostData
import com.pineapple.app.network.GfycatNetworkService
import com.pineapple.app.theme.PineappleTheme
import com.pineapple.app.util.toDp
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ExoVideoPlayer(
    url: String,
    modifier: Modifier,
    modifierVideo: BoxScope.() -> Modifier,
    playerControls: @Composable (Player) -> Unit,
    detailedView: Boolean = false
) {
    var playerController by remember { mutableStateOf<Player?>(null) }
    var playbackState by remember { mutableStateOf(ExoPlayer.STATE_IDLE) }
    var showPlayerControls by remember { mutableStateOf(true) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                showPlayerControls = !showPlayerControls
            }
    ) {
        AndroidView(
            modifier = modifierVideo(this),
            factory = { context ->
                StyledPlayerView(context).apply {
                    player = ExoPlayer.Builder(context).build().apply {
                        val dataSourceFactory = DefaultDataSource.Factory(context)
                        val internetVideoItem = MediaItem.fromUri(url)
                        val internetVideoSource = ProgressiveMediaSource
                            .Factory(dataSourceFactory)
                            .createMediaSource(internetVideoItem)
                        addMediaSource(internetVideoSource)
                        prepare()
                        useController = false
                        addListener(object : Player.Listener {
                            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackControlState: Int) {
                                super.onPlayerStateChanged(playWhenReady, playbackState)
                                playbackState = playbackControlState
                            }
                        })
                    }
                    playerController = player
                }
            }
        )
        AnimatedVisibility(
            visible = showPlayerControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            playerController?.let {
                playerControls(it)
            }
        }
    }
}

@Composable
fun GifVideoPlayer(
    modifier: Modifier = Modifier,
    url: String
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
    Image(
        painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(data = url)
                .apply { size(Size.ORIGINAL) }
                .build(),
            imageLoader = imageLoader
        ),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
fun MultiTypeMediaView(
    mediaHint: String,
    url: String,
    modifier: Modifier = Modifier,
    modifierVideo: (BoxScope.() -> Modifier) = { Modifier },
    richDomain: String? = null,
    gfycatService: GfycatNetworkService? = null,
    playerControls: @Composable (Player) -> Unit,
    expandToFullscreen: (() -> Unit)? = null,
    detailedView: Boolean = false,
    imageControls: (@Composable () -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (mediaHint) {
            "image", "link" -> {
                if (url.contains(".gif")) {
                    GifVideoPlayer(
                        url = url,
                        modifier = modifier.clickable {
                            expandToFullscreen?.invoke()
                        }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build().data,
                        contentDescription = null,
                        modifier = modifier.clickable {
                            expandToFullscreen?.invoke()
                        },
                        contentScale = ContentScale.FillWidth
                    )
                }
                imageControls?.invoke()
            }
            "hosted:video" -> {
                ExoVideoPlayer(
                    url = url.replace("amp;", ""),
                    modifier = modifier,
                    playerControls = playerControls,
                    detailedView = detailedView,
                    modifierVideo = modifierVideo
                )
            }
            "rich:video" -> {
                if (richDomain == "gfycat.com") {
                    gfycatService?.let {
                        var gifInformation by remember { mutableStateOf<GfycatObject?>(null) }
                        LaunchedEffect(true) {
                            gifInformation = it.fetchGif(
                                url.split("https://gfycat.com/")[1]
                            )
                        }
                        if (gifInformation != null) {
                            GifVideoPlayer(
                                url = gifInformation!!.gfyItem.gifUrl,
                                modifier = modifier.clickable {
                                    expandToFullscreen?.invoke()
                                }
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.secondary,
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }
                }
                imageControls?.invoke()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VideoControls(
    player: Player,
    postTitle: String,
    onExpand: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onBackPress: (() -> Unit)? = null,
    fullscreen: Boolean = false
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val context = LocalContext.current
    var videoProgress by remember { mutableStateOf(player.currentPosition) }
    var playPauseIcon by remember {
        mutableStateOf(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
    }
    var bottomControlHeight by remember { mutableStateOf(0.dp) }
    val updateProgress = object : Runnable {
        override fun run() {
            videoProgress = player.currentPosition
            mainHandler.postDelayed(this, 100)
        }
    }
    LaunchedEffect(true) {
        mainHandler.post(updateProgress)
    }
    PineappleTheme(useDarkTheme = if (fullscreen) false else isSystemInDarkTheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (fullscreen) {
                        Color.Black.copy(0.3F)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(0.3F)
                    }
                )
        ) {
            FilledTonalIconButton(
                onClick = {
                    playPauseIcon = if (player.isPlaying) {
                        player.pause()
                        R.drawable.ic_play_arrow
                    } else {
                        player.play()
                        R.drawable.ic_pause
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .shadow(elevation = 5.dp, shape = RoundedStarShape(sides = 9))
                    .align(Alignment.Center),
                shape = RoundedStarShape(sides = 9)
            ) {
                Icon(
                    painter = painterResource(id = playPauseIcon),
                    contentDescription = stringResource(id = R.string.ic_play_arrow_content_desc),
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(40.dp)
                )
            }
            if (player.contentDuration > 0) {
                FilledTonalIconButton(
                    onClick = { onExpand.invoke() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 15.dp, end = 15.dp)
                        .size(35.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (fullscreen) {
                            R.drawable.ic_close_fullscreen
                        } else {
                            R.drawable.ic_open_in_full
                        }),
                        contentDescription = stringResource(id = if (fullscreen) {
                            R.string.ic_close_fullscreen_content_desc
                        } else {
                            R.string.ic_open_in_full_content_desc
                        }),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (fullscreen) {
                    FilledTonalIconButton(
                        onClick = { onDownload?.invoke() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 15.dp, end = 65.dp)
                            .size(35.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = stringResource(id = R.string.ic_download_content_desc),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onBackPress?.invoke() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 15.dp, start = 15.dp)
                            .size(35.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = stringResource(id = R.string.ic_arrow_back_content_desc),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = postTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                bottom = bottomControlHeight + 20.dp,
                                start = 15.dp,
                                end = 15.dp
                            ),
                        color = MaterialTheme.colorScheme.surface
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            end = 10.dp,
                            start = 15.dp,
                            bottom = if (fullscreen) 10.dp else 0.dp
                        )
                        .onGloballyPositioned {
                            bottomControlHeight = it.size.height.toDp(context)
                        }
                ) {
                    Text(
                        text =  String.format(
                            format = "%02d:%02d",
                            videoProgress / 1000 / 60,
                            videoProgress / 1000 % 60
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Slider(
                        value = videoProgress.toFloat(),
                        onValueChange = {
                            videoProgress = it.toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(videoProgress)
                        },
                        valueRange = 0F..(player.contentDuration.toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                            inactiveTrackColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImageGifControls(
    postTitle: String,
    onBackPress: () -> Unit,
    onDownload: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
    ) {
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Scaffold(
                topBar = {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = { onBackPress.invoke() },
                            modifier = Modifier
                                .padding(top = 15.dp, start = 15.dp)
                                .size(35.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back),
                                contentDescription = stringResource(id = R.string.ic_arrow_back_content_desc),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Row {
                            FilledTonalIconButton(
                                onClick = { onDownload.invoke() },
                                modifier = Modifier
                                    .padding(top = 15.dp, end = 15.dp)
                                    .size(35.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_download),
                                    contentDescription = stringResource(id = R.string.ic_download_content_desc),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { onBackPress.invoke() },
                                modifier = Modifier
                                    .padding(top = 15.dp, end = 15.dp)
                                    .size(35.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_fullscreen),
                                    contentDescription = stringResource(R.string.ic_close_fullscreen_content_desc),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                containerColor = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = URLDecoder.decode(postTitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                top = it.calculateTopPadding(),
                                bottom = it.calculateBottomPadding() + 20.dp,
                                start = it.calculateStartPadding(LayoutDirection.Ltr) + 15.dp,
                                end = it.calculateEndPadding(LayoutDirection.Ltr) + 15.dp
                            )
                    )
                }
            }
        }
    }
}