package co.edu.konradlorenz.kapp.data.network

import co.edu.konradlorenz.kapp.data.network.KAppApi.Companion.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each body below is an example from docs/api/, as the mocks serve it. If a contract changes a
 * field these models read, the matching test stops compiling or stops passing before a screen
 * finds out at runtime.
 */
class ApiModelsTest {

    @Test
    fun `reads the token response of auth openapi`() {
        val tokens = json.decodeFromString<TokenResponse>(
            """
            {
              "accessToken": "eyJhbGciOiJSUzI1NiJ9.e30.sig",
              "tokenType": "Bearer",
              "expiresIn": 3600,
              "refreshToken": "rt_7Qm2Xk9PzT4vB8nL1cR6sW3yH5dF0gJ",
              "refreshExpiresIn": 2592000,
              "userId": "3f8a1c2e-7b4d-4e5a-9c6f-2d1b8e0a4c73",
              "roles": ["ROLE_STUDENT"]
            }
            """,
        )
        assertEquals(3600, tokens.expiresIn)
        assertEquals(listOf("ROLE_STUDENT"), tokens.roles)
    }

    @Test
    fun `reads a visitor pass, which has no refresh token`() {
        val tokens = json.decodeFromString<TokenResponse>(
            """
            {
              "accessToken": "eyJhbGciOiJSUzI1NiJ9.e30.sig",
              "tokenType": "Bearer",
              "expiresIn": 86400,
              "refreshToken": null,
              "refreshExpiresIn": null,
              "userId": "visitor:5c1d",
              "roles": ["ROLE_GUEST"]
            }
            """,
        )
        assertNull(tokens.refreshToken)
        assertEquals("visitor:5c1d", tokens.userId)
    }

    @Test
    fun `sends the client field, which the contract requires`() {
        val body = json.encodeToString(MicrosoftSignInRequest(idToken = "x".repeat(20)))
        assertTrue(body, body.contains("\"client\":\"APP\""))
    }

    @Test
    fun `reads a student profile with its academic block`() {
        val me = json.decodeFromString<UserProfile>(
            """
            {
              "id": "3f8a1c2e-7b4d-4e5a-9c6f-2d1b8e0a4c73",
              "email": "pepito.perez@konradlorenz.edu.co",
              "firstName": "Pepito",
              "lastName": "Perez Gomez",
              "avatarUrl": "https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg",
              "roles": ["ROLE_STUDENT"],
              "active": true,
              "academic": {
                "programCode": "506",
                "programName": "Ingeniería de Sistemas",
                "pensumCode": "1015",
                "currentLevel": 8
              }
            }
            """,
        )
        assertEquals("Pepito", me.firstName)
        assertEquals(8, me.academic?.currentLevel)
    }

    @Test
    fun `reads a staff profile, which has no academic block`() {
        val me = json.decodeFromString<UserProfile>(
            """
            {
              "id": "8e1c4f27-90ab-4d3e-b5f6-1a2b3c4d5e6f",
              "email": "ana.ruiz@konradlorenz.edu.co",
              "firstName": "Ana",
              "lastName": "Ruiz Mejía",
              "avatarUrl": null,
              "roles": ["ROLE_STAFF", "ROLE_ADMIN"],
              "active": true,
              "academic": null
            }
            """,
        )
        assertNull(me.academic)
        assertEquals(listOf("ROLE_STAFF", "ROLE_ADMIN"), me.roles)
    }

    @Test
    fun `ignores a field the client does not model yet`() {
        val health = json.decodeFromString<HealthStatus>("""{ "status": "UP", "version": "9" }""")
        assertEquals("UP", health.status)
    }
}
