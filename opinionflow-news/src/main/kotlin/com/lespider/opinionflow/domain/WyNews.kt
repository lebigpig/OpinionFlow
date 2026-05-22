package com.lespider.opinionflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "wynews")
class WyNews {
    @Id
    var id: Long? = null

    @Column(length = 1024)
    var title: String? = null

    @Lob
    var content: String? = null

    @Column(name = "`time`")
    var publishTime: LocalDateTime? = null
}
