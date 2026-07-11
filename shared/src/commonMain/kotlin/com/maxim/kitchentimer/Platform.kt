package com.maxim.kitchentimer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform