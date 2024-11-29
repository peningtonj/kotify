package com.dzirbel.kotify.repository.util

import com.dzirbel.kotify.repository.playlist.PlaylistTrackViewModel
import java.util.Dictionary

object SyncCalculator {

    sealed interface SyncOperation

    /**
     * A single operation which adds a list of [tracks] tracks at [position].
     */

    data class AddOperation(val tracks: List<PlaylistTrackViewModel>, val position: Int) : SyncOperation {
        init {
            require(position >= 0)
            require(tracks.isNotEmpty())
            require(tracks.size <= 100)
        }
    }

    data class SingleAddOperation(val track: PlaylistTrackViewModel, val position: Int) : SyncOperation {
        init {
            require(position >= 0)
        }
    }


    /**
     * A single operation which removes a list of [tracks] tracks at [position].
     */
    data class RemoveOperation(val tracks: List<PlaylistTrackViewModel>) : SyncOperation {
        init {
            require(tracks.isNotEmpty())
            require(tracks.size <= 100)
        }
    }

    /**
     * Calculates a sequence of [SyncOperation]s which will add or remove tracks as needed from a playlist
     *
     * TODO optimize to reduce number of operations when possible, i.e. merge neighboring moves that can be done in a
     *  single operation
     */
    fun calculateSyncOperations(
        local: List<PlaylistTrackViewModel>,
        remote: List<PlaylistTrackViewModel>
    ): List<SyncOperation> {
        if (local.size <= 1) return emptyList()

        val result = mutableListOf<SyncOperation>()
        val toRemove = remote.filter { !local.contains(it) }

        toRemove.chunked(100).map { chunk ->
            result.add(RemoveOperation(chunk))
        }

        val toAdd = local.filter { !remote.contains(it) }
        val addOperations = mutableListOf<SingleAddOperation>()
        toAdd.map { track ->
            val addBefore = local[track.indexOnPlaylist + 1]
            val addPosition = remote.find { nextTrack -> nextTrack.track?.id == addBefore.track?.id }?.indexOnPlaylist
            addPosition?.let { SingleAddOperation(track, it) }?.let { addOperations.add(it) }
        }

        val combinedAddOperations = addOperations
            .groupBy({ it.position }, { it.track })

        combinedAddOperations.forEach { (position, tracks) ->
            result.add(AddOperation(tracks.sortedBy { track ->
                track.indexOnPlaylist
            }, position))
        }

        return result
    }
}
