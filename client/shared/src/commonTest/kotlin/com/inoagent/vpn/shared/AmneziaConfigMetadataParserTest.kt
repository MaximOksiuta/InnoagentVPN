package com.inoagent.vpn.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmneziaConfigMetadataParserTest {
    @Test
    fun parsesEndpointAndSafeName() {
        val result = AmneziaConfigMetadataParser.parse(
            "Frankfurt premium.conf",
            """[Interface]
                |PrivateKey = secret
                |Jc = 5
                |[Peer]
                |Endpoint = vpn.example.org:51820
            """.trimMargin(),
        )
        assertEquals("Frankfurt-premi", result.suggestedName)
        assertEquals("vpn.example.org:51820", result.endpoint)
    }

    @Test
    fun rejectsMissingPeer() {
        assertFailsWith<IllegalArgumentException> {
            AmneziaConfigMetadataParser.parse("broken.conf", "[Interface]\nPrivateKey = x")
        }
    }
}

