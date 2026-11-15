package dev.satooru.toki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

private val ITEM_HEIGHT = 48.dp
private const val VISIBLE_COUNT = 5

/**
 * iPhone風の縦スクロール(ドラムロール)ピッカー。
 * 循環させない: 先頭と末尾はつながらず、端で止まる(例: 59 と 0 は行き来できない)。
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 72.dp,
    fontSize: TextUnit = 28.sp,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val itemHeightPx = with(LocalDensity.current) { ITEM_HEIGHT.toPx() }
    val currentOnSelected by rememberUpdatedState(onSelected)
    val centerIndex = {
        val raw = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
        raw.coerceIn(0, items.lastIndex)
    }

    LaunchedEffect(state, items.size) {
        snapshotFlow { centerIndex() }.distinctUntilChanged().collect { currentOnSelected(it) }
    }

    Box(modifier.width(itemWidth).height(ITEM_HEIGHT * VISIBLE_COUNT), contentAlignment = Alignment.Center) {
        // 中央の選択帯
        Box(
            Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        )
        val center = centerIndex()
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            // 上下に余白を置き、先頭/末尾の項目も中央に来られるようにする(循環はしない)
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = ITEM_HEIGHT * (VISIBLE_COUNT / 2)),
        ) {
            itemsIndexed(items) { index, text ->
                Box(Modifier.fillMaxWidth().height(ITEM_HEIGHT), contentAlignment = Alignment.Center) {
                    val selected = index == center
                    Text(
                        text,
                        fontSize = fontSize, // 選択中でも大きさは変えない
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 時・分(12時間表記ならAM/PM付き)のホイールピッカー */
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    itemWidth: Dp = 64.dp,
) {
    val minutes = minutes60
    Row(
        Modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (is24Hour) {
            WheelPicker(
                items = hours24, selectedIndex = hour, itemWidth = itemWidth,
                onSelected = { onChange(it, minute) },
            )
        } else {
            val pm = hour >= 12
            val display = if (hour % 12 == 0) 12 else hour % 12
            WheelPicker(
                items = listOf("午前", "午後"), selectedIndex = if (pm) 1 else 0, itemWidth = itemWidth,
                onSelected = { onChange((hour % 12) + if (it == 1) 12 else 0, minute) },
            )
            WheelPicker(
                items = hours12, selectedIndex = display - 1, itemWidth = itemWidth,
                onSelected = { onChange(((it + 1) % 12) + if (pm) 12 else 0, minute) },
            )
        }
        Text(":", fontSize = 30.sp)
        WheelPicker(
            items = minutes, selectedIndex = minute,
            onSelected = { onChange(hour, it) },
        )
    }
}

private val hours24 = (0..23).map { "%02d".format(it) }
private val hours12 = (1..12).map { it.toString() }
private val minutes60 = (0..59).map { "%02d".format(it) }
