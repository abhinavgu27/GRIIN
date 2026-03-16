package com.abhinav.griinserver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/hazards")
class HazardController(private val repository: HazardRepository) {

    @PostMapping
    fun createHazard(@RequestBody report: HazardReport): ResponseEntity<Void> {
        repository.save(report)
        println("🟢 HAZARD RECEIVED FROM APP!!! Latitude: ${report.latitude}")
        return ResponseEntity.ok().build()
    }
    @GetMapping
    fun getAllHazards(): ResponseEntity<List<HazardReport>> {
        val hazards = repository.findAll()
        return ResponseEntity.ok(hazards) // This sends the full list from Neon to the app
    }
}
