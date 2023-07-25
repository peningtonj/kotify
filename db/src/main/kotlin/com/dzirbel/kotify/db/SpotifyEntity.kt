package com.dzirbel.kotify.db

import com.dzirbel.kotify.network.model.SpotifyObject
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Base class for tables which contain a [SpotifyEntity], and provides common columns like [name].
 */
abstract class SpotifyEntityTable(name: String = "") : StringIdTable(name = name) {
    val name: Column<String> = text("name")
    val uri: Column<String?> = text("uri").nullable()

    val createdTime: Column<Instant> = timestamp("created_time").clientDefault { Instant.now() }
    val updatedTime: Column<Instant> = timestamp("updated_time").clientDefault { Instant.now() }
    val fullUpdatedTime: Column<Instant?> = timestamp("full_updated_time").nullable()
}

/**
 * Base class for entity objects in a [SpotifyEntityTable].
 *
 * TODO refactor UI to avoid direct use of (unstable) database entities
 */
abstract class SpotifyEntity(id: EntityID<String>, table: SpotifyEntityTable) : Entity<String>(id) {
    var name: String by table.name
    var uri: String? by table.uri

    var createdTime: Instant by table.createdTime
    var updatedTime: Instant by table.updatedTime
    var fullUpdatedTime: Instant? by table.fullUpdatedTime // TODO practically unused
}

/**
 * Base [EntityClass] which serves as the companion object for a [SpotifyEntityTable].
 */
abstract class SpotifyEntityClass<EntityType : SpotifyEntity, NetworkType : SpotifyObject>(table: SpotifyEntityTable) :
    EntityClass<String, EntityType>(table) {

    /**
     * Convenience function which finds and updates the [EntityType] with the given [id] or creates a new one if none
     * exists; in either case, the entity is returned.
     *
     * This consolidates logic to set common properties like [SpotifyEntity.updatedTime] and [SpotifyEntity.name] as
     * well as calling [update] in either case of finding or creating an entity.
     */
    fun updateOrInsert(id: String, networkModel: NetworkType, update: EntityType.() -> Unit): EntityType {
        return findById(id)
            ?.apply {
                updatedTime = Instant.now()
                networkModel.name?.let { name = it }
                uri = networkModel.uri
                update()
            }
            ?: new(id = id) {
                networkModel.name?.let { name = it }
                uri = networkModel.uri
                update()
            }
    }
}
