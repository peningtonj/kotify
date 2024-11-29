package com.dzirbel.kotify.ui.properties

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dzirbel.kotify.network.model.SpotifyTrack
import com.dzirbel.kotify.repository.player.Player
import com.dzirbel.kotify.repository.track.TrackViewModel
import com.dzirbel.kotify.ui.CachedIcon
import com.dzirbel.kotify.ui.LocalPlayer
import com.dzirbel.kotify.ui.components.adapter.SortOrder
import com.dzirbel.kotify.ui.components.table.Column
import com.dzirbel.kotify.ui.components.table.ColumnWidth
import com.dzirbel.kotify.ui.theme.Dimens

/**
 * A [Column] which displays the current play state of a track of type [T] (abstract to support both actual tracks and
 * playlist tracks) with an icon, and allows playing a [SpotifyTrack] via the [playContextFromTrack].
 */
class TrackQueueColumn<T> : Column<T> {
    override val title = "Add to Queue"
    override val width = ColumnWidth.Fill()
    override val cellAlignment = Alignment.Center

    @Composable
    override fun Header(sortOrder: SortOrder?, onSetSort: (SortOrder?) -> Unit) {
        Box(Modifier)
    }

    @Composable
    override fun Item(item: T) {
        val hoverInteractionSource = remember { MutableInteractionSource() }

        val player = LocalPlayer.current

        val size = Dimens.iconSmall
        Box(Modifier.padding(Dimens.space1).size(size).hoverable(hoverInteractionSource)) {
            val context = Player.PlayContext((item as TrackViewModel).uri)
            IconButton(
                onClick = { player.addToQueue(context) },
            ) {
                CachedIcon(
                    name = "queue-music",
                    size = size,
                    contentDescription = "Play",
                )
            }
        }
    }
}
