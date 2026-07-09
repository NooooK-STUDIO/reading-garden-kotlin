package std.nooook.readinggardenkotlin.modules.book.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity

@Entity
@Table(name = "books")
class BookEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_id")
    var garden: GardenEntity? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var title: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var author: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var publisher: String = "",
    @Column(nullable = false)
    var status: Int = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false)
    var page: Int = 0,
    var rating: Int? = null,
    @Column(length = 30)
    var isbn: String? = null,
    @Column(length = 30)
    var tree: String? = null,
    @Column(columnDefinition = "TEXT")
    var imageUrl: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var info: String = "",
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    protected constructor() : this(user = UserEntity())
}
