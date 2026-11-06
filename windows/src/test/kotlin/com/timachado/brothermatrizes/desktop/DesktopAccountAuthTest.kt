package com.timachado.brothermatrizes.desktop

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopAccountAuthTest {
    private val token="token-de-teste-sem-credencial-123456789000"
    private fun ent(plan:String="free",pro:Boolean=false, id:String="12345678-aaaa-bbbb-cccc-123456789abc",status:String="active",period:String="null"):String =
        """{"authenticated":true,"user":{"id":"$id","email":"qa@example.com"},
        "planCode":"$plan","pro":$pro,"status":"$status",
        "currentPeriodEnd":$period}"""
    private class Fake(var entitlement:String):DesktopAuthGateway {
        var down=false
        var confirmed=true
        val calls=mutableListOf<String>()
        override fun fetch(path:String, method:String,payload:String?, token:String?):DesktopAuthReply{
            calls+=path
            if(path=="/auth/v1/logout")return DesktopAuthReply(204,"")
            if(path.startsWith("/auth/v1/token"))return DesktopAuthReply(
                200,"""{"access_token":"token-de-teste-sem-credencial-123456789000",
                "expires_in":3600,"token_type":"bearer"}"""
            )
            assertEquals("token-de-teste-sem-credencial-123456789000",token)
            if(path=="/auth/v1/user")return DesktopAuthReply(
                200, """{"id":"12345678-aaaa-bbbb-cccc-123456789abc",
                "email":"qa@example.com",
                "email_confirmed_at":${if(confirmed) "\"2026-10-01T00:00:00Z\"" else "null"}}"""
            )
            return if(down)DesktopAuthReply(503,"{}")
                else DesktopAuthReply(200,entitlement)
        }
    }
    @Test fun `login confirmado não concede PRO local`()=runBlocking{
        val fake=Fake(ent())
        val service=DesktopAccountAuth(fake)
        val result=service.login("qa@example.com","senhaSeguro123")
        assertEquals("free",result.plan)
        assertFalse(result.backendReportsPro)
        assertFalse(result.windowsProFeaturesEnabled)
        assertTrue(service.isSignedIn)
        assertTrue("/functions/v1/brother-entitlement" in fake.calls)
        service.logout()
        assertFalse(service.isSignedIn)
    }
    @Test fun `assinatura confirmada pelo servidor apenas informa estado`()=runBlocking{
        val expiration=Instant.now().plusSeconds(3600).toString()
        val fake=Fake(ent("pro_monthly",true,period="\"$expiration\""))
        val service=DesktopAccountAuth(fake)
        val state=service.login("qa@example.com","senhaSeguro123")
        assertTrue(state.backendReportsPro)
        assertFalse(state.windowsProFeaturesEnabled)
        fake.entitlement=ent()
        assertFalse(service.revalidate().backendReportsPro)
    }
    @Test fun `licença expirada falsificada ou conta incorreta falha fechado`()=runBlocking{
        val invalid=listOf(
            ent("pro_monthly",true,period="\"2025-01-01T00:00:00Z\""),
            ent("pro_lifetime",true,status="expired"),
            ent("pro_lifetime",false),
            ent("fake",true)
        )
        invalid.forEach{
            val state=DesktopAccountAuth(Fake(it)).login("qa@example.com","senhaSeguro123")
            assertFalse(state.backendReportsPro)
        }
        val service=DesktopAccountAuth(Fake(ent("pro_lifetime",true,
            id="00000000-0000-0000-0000-000000000000")))
        assertFailsWith<IllegalStateException>{
            service.login("qa@example.com","senhaSeguro123")
        }
        assertFalse(service.isSignedIn)
    }
    @Test fun `falha de consulta revoga estado local`()=runBlocking{
        val fake=Fake(ent("pro_lifetime",true))
        val service=DesktopAccountAuth(fake)
        service.login("qa@example.com","senhaSeguro123")
        fake.down=true
        assertFailsWith<IllegalStateException>{service.revalidate()}
        assertFalse(service.isSignedIn)
    }
    @Test fun `não aceita email não confirmado mesmo com sessão Supabase`()=runBlocking{
        val fake=Fake(ent("pro_lifetime",true))
        fake.confirmed=false
        val service=DesktopAccountAuth(fake)
        assertFailsWith<IllegalStateException>{
            service.login("qa@example.com","senhaSeguro123")
        }
        assertFalse(service.isSignedIn)
        assertEquals(null,service.current)
    }
    @Test fun `não aceita entrada malformada`()=runBlocking{
        val service=DesktopAccountAuth(Fake(ent()))
        assertFailsWith<IllegalArgumentException>{
            service.login("invalido","senhaSeguro123")
        }
        assertFalse(service.isSignedIn)
    }
}
