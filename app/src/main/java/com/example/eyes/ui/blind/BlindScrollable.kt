package com.example.eyes.ui.blind

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

private const val BLIND_SCROLL_STEP_PX = 640

fun Modifier.blindScrollable(
    id: String,
    scrollState: ScrollState
): Modifier = composed {
    val manager = LocalBlindScrollManager.current

    DisposableEffect(manager, id, scrollState) {
        manager?.registerOrUpdate(
            BlindScrollTarget(
                id = id,
                canScrollForward = { scrollState.value < scrollState.maxValue },
                canScrollBackward = { scrollState.value > 0 },
                scrollForward = {
                    scrollState.animateScrollTo(
                        (scrollState.value + BLIND_SCROLL_STEP_PX).coerceAtMost(scrollState.maxValue)
                    )
                },
                scrollBackward = {
                    scrollState.animateScrollTo(
                        (scrollState.value - BLIND_SCROLL_STEP_PX).coerceAtLeast(0)
                    )
                }
            )
        )

        onDispose {
            manager?.unregister(id)
        }
    }

    this
}

fun Modifier.blindLazyScrollable(
    id: String,
    listState: LazyListState
): Modifier = composed {
    val manager = LocalBlindScrollManager.current

    DisposableEffect(manager, id, listState) {
        manager?.registerOrUpdate(
            BlindScrollTarget(
                id = id,
                canScrollForward = { listState.canScrollForward },
                canScrollBackward = { listState.canScrollBackward },
                scrollForward = {
                    listState.animateScrollToItem(
                        (listState.firstVisibleItemIndex + 2).coerceAtMost(listState.layoutInfo.totalItemsCount - 1)
                    )
                },
                scrollBackward = {
                    listState.animateScrollToItem(
                        (listState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                    )
                }
            )
        )

        onDispose {
            manager?.unregister(id)
        }
    }

    this
}
