package com.inoagent.vpn.desktop

import kotlin.test.*

class DesktopProfilesTest {
    @Test fun `profiles survive store recreation without changing AWG contents`() {
        val vault=PreviewVault();val store=DesktopProfiles(vault)
        val raw="[Interface]\nPrivateKey = secret\nS4 = 14\nI1 = <b 0x12>\n[Peer]\nEndpoint = host:1234"
        val profile=store.import("Мой Лондон.conf",raw)
        val restored=DesktopProfiles(vault)
        assertEquals(profile,restored.list().single());assertEquals(raw,restored.config(profile.id))
    }
    @Test fun `delete only removes selected local configuration`() {
        val store=DesktopProfiles(PreviewVault());val raw="[Interface]\n[Peer]\nEndpoint = host:1"
        val first=store.import("first.conf",raw);val second=store.import("second.conf",raw)
        store.delete(first.id);assertEquals(listOf(second),store.list());assertEquals(raw,store.config(second.id));assertFails { store.config(first.id) }
    }
}
