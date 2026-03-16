package com.abhinav.griinserver

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class HazardReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var severity: Double = 0.0
)
