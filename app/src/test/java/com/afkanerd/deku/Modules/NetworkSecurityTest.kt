package com.afkanerd.deku.Modules

import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkSecurityTest {
    @Test
    fun gatewayUrlsRequireHttps() {
        Network.requireHttps("https://gateway.example.test/messages")
        assertThrows(IllegalArgumentException::class.java) {
            Network.requireHttps("http://gateway.example.test/messages")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Network.requireHttps("not-a-url")
        }
    }
}
