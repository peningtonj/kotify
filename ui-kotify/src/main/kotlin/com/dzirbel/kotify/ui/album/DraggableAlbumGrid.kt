package com.dzirbel.kotify.ui.album

/*
 * Copyright 2022 André Claßen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.dzirbel.kotify.network.model.SpotifyAlbum
import com.dzirbel.kotify.repository.album.AlbumViewModel
import com.dzirbel.kotify.ui.components.LoadedImage
import com.dzirbel.kotify.ui.components.adapter.ListAdapter
import org.burnoutcrew.reorderable.*


@Composable
fun DraggableAlbumGrid(
    albums : ListAdapter<AlbumViewModel>,
    modifier: Modifier = Modifier,
) {

    val data = remember { mutableStateOf(List(16) { id -> albums[id]!! }) }
    val state = rememberReorderableLazyGridState(
        onMove = { from, to ->
            data.value = data.value.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        })
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state.gridState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.reorderable(state)
    ) {
        items(data.value, { it }) { item ->
            ReorderableItem(state, key = item, defaultDraggingModifier = Modifier) { isDragging ->
                val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colors.primary)
                        .shadow(elevation.value)
                        .border(3.dp, MaterialTheme.colors.surface)
                        .detectReorderAfterLongPress(state)
                ) {
                    LoadedImage(item, modifier = Modifier.detectReorder(state))
                }
            }
        }
    }
}


//
//val albumList = albums.toList()
//println(albumList.size)
//val data = remember { mutableStateOf(List(albumList.size) { albumList[it] }) }
//val state = rememberReorderableLazyGridState(
//    onMove = { from, to ->
//        data.value = data.value.toMutableList().apply {
//            add(to.index, removeAt(from.index))
//        }
//    })
//LazyVerticalGrid(
//columns = GridCells.Fixed(4),
//state = state.gridState,
//modifier = Modifier.reorderable(state)
//) {
//    itemsIndexed(albums.toList()) { index, item ->
//        ReorderableItem(state, key = index, defaultDraggingModifier = Modifier) { isDragging ->
//            val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
//            val backgroundColor = if (isDragging) Color.Blue else Color.Red
//
//            Card(
//                backgroundColor = backgroundColor,
//            ) {
//                Text(item.name)
//            }
//        }
//    }
//}
