package net.bjoernpetersen.deskbot.rest.model

import net.bjoernpetersen.musicbot.api.auth.UserManager
import net.bjoernpetersen.musicbot.spi.player.SongQueue

/**
 * @param song a Song
 * @param userName The user who put the song in the queue. May be missing if it was auto suggested.
 */
data class SongEntry(val song: Song, val userName: String?)

private typealias CoreSongEntry = net.bjoernpetersen.musicbot.api.player.SongEntry

fun CoreSongEntry.toModel() = SongEntry(song, user?.name)

/**
 * @param song a Song
 * @param userName The user who put the song in the queue.
 */
data class QueueEntry(val song: Song, val userName: String)

typealias CoreQueueEntry = net.bjoernpetersen.musicbot.api.player.QueueEntry

fun CoreQueueEntry.toModel() = QueueEntry(song, user.name)
// TODO this may be ready to remove
fun QueueEntry.toCore(userManager: UserManager) =
    CoreQueueEntry(song, userManager.getUser(userName))

fun SongQueue.toModel(): List<QueueEntry> {
    return toList().map { it.toModel() }
}

fun List<CoreSongEntry>.toModel(): List<SongEntry> {
    return map { it.toModel() }
}
