package com.inoagent.vpn.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InoagentApiTest {
    private val jsonHeaders=headersOf(HttpHeaders.ContentType,"application/json")
    @Test fun `formatted phones match website normalization`() {
        assertEquals("+79991234567",normalizeServicePhone("8 (999) 123-45-67"))
        assertEquals("+79991234567",normalizeServicePhone("9991234567"))
        assertEquals("+447911123456",normalizeServicePhone("+44 7911 123456"))
        assertFailsWith<IllegalArgumentException> { normalizeServicePhone("123") }
    }
    @Test fun `authentication uses bearer and tolerates extra server fields`()=runTest {
        var bearer=""
        val api=InoagentApi(HttpClient(MockEngine { request->
            when(request.url.encodedPath) {
                "/api/auth/login"->{ assertTrue(request.body.toByteArray().decodeToString().contains("+79991234567"));respond("""{"accessToken":"test-token","expiresIn":9000,"tokenType":"Bearer"}""",headers=jsonHeaders) }
                else->{bearer=request.headers[HttpHeaders.Authorization].orEmpty();respond("""{"id":1,"phone":"+79991234567","nickname":"Test","isAdmin":false,"telegramId":null}""",headers=jsonHeaders)}
            }
        }),"https://service.test")
        assertEquals("test-token",api.login("8 (999) 123-45-67","password"))
        assertEquals("Test",api.me().nickname);assertEquals("Bearer test-token",bearer);api.close()
    }
    @Test fun `existing config avoids generation and retains every AWG field`()=runTest {
        val raw="[Interface]\nPrivateKey = private\nI1 = <b 0x123>\nHeaderProtectionKey = abc\n[Peer]\nEndpoint = host:1234"
        val payload=kotlinx.serialization.json.Json.encodeToString(SiteDeviceDetails.serializer(),SiteDeviceDetails(4,"Phone",listOf(SiteConfig(8,5,"London","UK",raw))))
        val paths=mutableListOf<String>()
        val api=InoagentApi(HttpClient(MockEngine { request->paths+=request.url.encodedPath;respond(payload,headers=jsonHeaders) }),"https://service.test")
        api.setToken("token");val config=api.configuration(4,5,"request-123")
        assertEquals(raw,config.config);assertEquals(listOf("/api/devices/4"),paths);assertFalse(config.toString().contains("private"));api.close()
    }
    @Test fun `queue uses only current device and downloads completed result`()=runTest {
        var requests=0
        var fetched=0
        val api=InoagentApi(HttpClient(MockEngine { request->
            val body=when(request.url.encodedPath) {
                "/api/devices/4"->{fetched++;if(fetched==1) """{"id":4,"name":"Phone","configs":[]}""" else """{"id":4,"name":"Phone","configs":[{"id":8,"serverId":5,"serverName":"London","serverLocation":"UK","config":"FULL_AWG_CONFIG"}]}"""}
                "/api/generation/jobs"->{requests++;val posted=request.body.toByteArray().decodeToString();assertTrue(posted.contains("\"deviceIds\":[4]"));assertFalse(posted.contains("allUsers"));"""{"id":"job-one","state":"queued","items":[{"deviceId":4,"state":"queued"}]}"""}
                else->"""{"id":"job-one","state":"completed","items":[{"deviceId":4,"state":"done","configId":8}]}"""
            }
            respond(body,headers=jsonHeaders)
        }),"https://service.test")
        api.setToken("token");assertEquals("FULL_AWG_CONFIG",api.configuration(4,5,"request-123").config);assertEquals(1,requests);api.close()
    }
    @Test fun `unauthorized requests return a useful error and never server body secrets`()=runTest {
        val api=InoagentApi(HttpClient(MockEngine { respond("SECRET",HttpStatusCode.Unauthorized,jsonHeaders) }),"https://service.test")
        api.setToken("token")
        val exception=assertFailsWith<ServiceException> { api.me() }
        assertEquals(401,exception.status);assertFalse(exception.message.orEmpty().contains("SECRET"));api.close()
    }
    @Test fun `failed queue result is surfaced without claiming a connection`()=runTest {
        val api=InoagentApi(HttpClient(MockEngine { request->
            respond(when(request.url.encodedPath) {
                "/api/devices/4"->"""{"id":4,"name":"Phone","configs":[]}"""
                else->"""{"id":"job-one","state":"partial","items":[{"deviceId":4,"state":"failed","error":"Сервер недоступен"}]}"""
            },headers=jsonHeaders)
        }),"https://service.test")
        api.setToken("token");assertEquals(422,assertFailsWith<ServiceException> { api.configuration(4,5,"request-123") }.status);api.close()
    }
}
