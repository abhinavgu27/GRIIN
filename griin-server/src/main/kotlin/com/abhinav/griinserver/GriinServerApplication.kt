package com.abhinav.griinserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GriinServerApplication

fun main(args: Array<String>) {
	runApplication<GriinServerApplication>(*args)
}
