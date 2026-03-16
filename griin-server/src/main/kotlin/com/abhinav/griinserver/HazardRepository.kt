package com.abhinav.griinserver

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HazardRepository : JpaRepository<HazardReport, Long>
