package com.faraontvplus.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF100C20)
private val Panel = Color(0xFF1B1630)
private val Purple = Color(0xFF9B7BFF)
private val Muted = Color(0xFFB7B0CB)
private val Categories = listOf("Todos", "Noticias", "Deportes", "Entretenimiento", "Infantil", "Películas")
private val PlaylistUrls = listOf(
    "https://iptv-org.github.io/iptv/countries/mx.m3u",
    "https://iptv-org.github.io/iptv/languages/spa.m3u",
    "https://iptv-org.github.io/iptv/categories/sports.m3u"
)

data class TvChannel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val country: String = "",
    val id: String = ""
) {
    val key: String get() = id.ifBlank { url }
    val category: String get() {
        val s = "$group $name".lowercase()
        return when {
            listOf("news", "noticia", "24 horas", "cnn").any(s::contains) -> "Noticias"
            listOf("sport", "deport", "futbol", "fútbol", "football", "espn", "fox sports").any(s::contains) -> "Deportes"
            listOf("kids", "niñ", "infantil", "cartoon", "disney", "nickelodeon").any(s::contains) -> "Infantil"
            listOf("movie", "película", "pelicula", "cinema", "cine", "film").any(s::contains) -> "Películas"
            listOf("entertainment", "entretenimiento", "music", "música", "variety").any(s::contains) -> "Entretenimiento"
            else -> "Todos"
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 12, 32)
        window.navigationBarColor = android.graphics.Color.rgb(16, 12, 32)
        setContent { FaraonApp() }
    }

    fun setImmersiveMode(enabled: Boolean) {
        window.decorView.systemUiVisibility = if (enabled) {
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onDestroy() {
        setImmersiveMode(false)
        super.onDestroy()
    }
}

private suspend fun loadChannels(): List<TvChannel> = withContext(Dispatchers.IO) {
    val all = LinkedHashMap<String, TvChannel>()
    for (playlist in PlaylistUrls) {
        try {
            val connection = (URL(playlist).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 12000
                setRequestProperty("User-Agent", "FaraonTVPlus/1.0 Android")
            }
            connection.inputStream.bufferedReader().use { reader ->
                var name = ""
                var logo = ""
                var group = ""
                var country = ""
                var id = ""
                for (raw in reader.lineSequence()) {
                    if (all.size >= 1800) break
                    val line = raw.trim()
                    if (line.startsWith("#EXTINF:", true)) {
                        name = line.substringAfter(',').trim().ifBlank { "Canal sin nombre" }
                        fun attr(key: String) = Regex("$key=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1).orEmpty()
                        logo = attr("tvg-logo")
                        group = attr("group-title")
                        country = attr("tvg-country")
                        id = attr("tvg-id")
                    } else if ((line.startsWith("https://", true) || line.startsWith("http://", true)) && name.isNotBlank()) {
                        val c = TvChannel(name, line, logo, group, country, id)
                        if (!all.containsKey(c.url) && all.size < 1800) all[c.url] = c
                        name = ""; logo = ""; group = ""; country = ""; id = ""
                    }
                }
            }
            connection.disconnect()
        } catch (_: Exception) {
            // Continue to the next public playlist when one source is unavailable.
        }
    }
    all.values.toList()
}

@Composable
private fun FaraonApp() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadMessage by remember { mutableStateOf("Conectando con el catálogo público…") }
    var category by remember { mutableStateOf("Todos") }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sortByCountry by remember { mutableStateOf(false) }
    val preferences = remember { context.getSharedPreferences("faraon_tv_plus", 0) }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favorites", emptySet<String>())?.toSet() ?: emptySet()) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<TvChannel?>(null) }
    var playerError by remember { mutableStateOf("") }
    var fullScreen by remember { mutableStateOf(false) }
    var playerReady by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                playerError = buildString {
                    append("Error de reproducción: ")
                    append(error.errorCodeName)
                    if (detail != null) {
                        append("\n")
                        append(detail.take(180))
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    playerReady = true
                    playerError = ""
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(reloadToken) {
        loading = true
        val loaded = loadChannels()
        channels = loaded
        loading = false
        loadMessage = if (loaded.isEmpty()) "No se pudo cargar el catálogo. Comprueba tu conexión e inténtalo de nuevo." else ""
    }

    LaunchedEffect(favorites) {
        preferences.edit().putStringSet("favorites", favorites.toMutableSet()).apply()
    }

    BackHandler(enabled = selected != null || favoritesOnly || category != "Todos" || query.isNotEmpty()) {
        if (fullScreen) {
            fullScreen = false
            activity?.setImmersiveMode(false)
        } else if (selected != null) {
            selected = null
            player.stop()
            activity?.setImmersiveMode(false)
        } else {
            favoritesOnly = false
            category = "Todos"
            query = ""
        }
    }

    val filtered = remember(channels, category, query, favoritesOnly, favorites, sortByCountry) {
        channels.filter { c ->
            (category == "Todos" || c.category == category) &&
                (!favoritesOnly || favorites.contains(c.key)) &&
                (query.isBlank() || "${c.name} ${c.group} ${c.country}".contains(query, true))
        }.sortedWith(if (sortByCountry) compareBy<TvChannel> { it.country.lowercase() }.thenBy { it.name.lowercase() } else compareBy<TvChannel> { it.name.lowercase() })
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Purple,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize()
                .then(if (fullScreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                .background(Ink)
        ) {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Purple), contentAlignment = Alignment.Center) {
                        Text("▶", color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Column {
                        Text("FARAÓN TV PLUS", fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text("Televisión en vivo", color = Muted, fontSize = 11.sp)
                    }
                }
                TextButton(onClick = { favoritesOnly = !favoritesOnly }) {
                    Text(if (favoritesOnly) "♥ Favoritos" else "♡ Favoritos", color = Purple)
                }
            }

            if (selected != null) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("● EN VIVO", color = Color(0xFFFF667A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(selected!!.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = {
                            fullScreen = !fullScreen
                            activity?.setImmersiveMode(fullScreen)
                        }) { Text(if (fullScreen) "Salir pantalla completa" else "⛶ Pantalla completa", color = Purple) }
                        TextButton(onClick = {
                            selected = null
                            player.stop()
                            fullScreen = false
                            activity?.setImmersiveMode(false)
                        }) { Text("Cerrar", color = Muted) }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                }
                            },
                            update = { it.player = player }
                        )
                        if (!playerReady && playerError.isBlank()) CircularProgressIndicator(color = Purple)
                        if (playerError.isNotBlank()) Text(playerError, Modifier.padding(20.dp), color = Color.White)
                    }
                    Text("Si un canal falla, prueba otra señal. La disponibilidad depende del proveedor.", color = Muted, fontSize = 12.sp)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text("Tu mundo. En directo.", fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Text("Canales públicos y gratuitos · La disponibilidad puede variar.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⌕", color = Purple, fontSize = 23.sp)
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Buscar canal…", color = Muted)
                                inner()
                            }
                        )
                        if (query.isNotEmpty()) Text("✕", Modifier.clickable { query = "" }.padding(4.dp), color = Muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Categories) { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = { category = item; favoritesOnly = false },
                                label = { Text(item) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Purple,
                                    selectedLabelColor = Ink
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (favoritesOnly) "Mis favoritos" else "Canales disponibles", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (loading) "Cargando…" else "${filtered.size} canales", color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { sortByCountry = !sortByCountry }) { Text(if (sortByCountry) "País" else "A–Z", color = Purple, fontSize = 12.sp) }
                    }
                    if (loading) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Purple)
                                Spacer(Modifier.height(12.dp))
                                Text(loadMessage, color = Muted)
                            }
                        }
                    } else if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(loadMessage.ifBlank { "No hay canales con ese filtro." }, color = Muted)
                                Spacer(Modifier.height(10.dp))
                                TextButton(onClick = { query = ""; category = "Todos"; favoritesOnly = false; reloadToken++ }) {
                                    Text("Volver a cargar", color = Purple)
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 145.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            gridItems(filtered, key = { it.url }) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    favorite = favorites.contains(channel.key),
                                    onToggleFavorite = {
                                        favorites = if (favorites.contains(channel.key)) favorites - channel.key else favorites + channel.key
                                    },
                                    onClick = {
                                        selected = channel
                                        playerReady = false
                                        playerError = ""
                                        try {
                                            player.stop()
                                            val mediaItem = MediaItem.Builder()
                                                .setUri(channel.url)
                                                .apply {
                                                    if (channel.url.contains(".m3u8", ignoreCase = true)) {
                                                        setMimeType(MimeTypes.APPLICATION_M3U8)
                                                    }
                                                }
                                                .build()
                                            player.setMediaItem(mediaItem)
                                            player.prepare()
                                            player.playWhenReady = true
                                        } catch (_: Exception) {
                                            playerError = "No se pudo abrir esta señal. Prueba otro canal."
                                            playerReady = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: TvChannel, favorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
            .clickable(onClick = onClick).focusable().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B2345)), contentAlignment = Alignment.Center) {
                if (channel.logo.isNotBlank()) { AsyncImage(model = channel.logo, contentDescription = channel.name, modifier = Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit) } else { Text(channel.name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase(), color = Purple, fontWeight = FontWeight.Black) }
            }
            Text(if (favorite) "♥" else "♡", Modifier.clickable { onToggleFavorite() }.padding(8.dp), color = if (favorite) Color(0xFFFF6C91) else Muted, fontSize = 22.sp)
        }
        Text(channel.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(channel.group.ifBlank { channel.country.ifBlank { "TV en vivo" } }, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("● LIVE", color = Color(0xFFFF667A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
