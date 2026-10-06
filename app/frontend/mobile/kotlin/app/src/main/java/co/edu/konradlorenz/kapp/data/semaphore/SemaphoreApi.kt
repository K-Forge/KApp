package co.edu.konradlorenz.kapp.data.semaphore

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/api/semaphore.openapi.yaml, as far as the app uses it.
 *
 * Everything under `/api/semaphore` is a student's own and answers `403` to anybody else, which is
 * why the app only shows the tab to a student. Nothing here writes a status or a grade: those are
 * SINU's, and the contract has no route for them. The plans are the one thing a student writes.
 */
interface SemaphoreApi {

    @GET("api/semaphore/me")
    suspend fun mySemaphore(): StudentSemaphore

    @GET("api/semaphore/me/summary")
    suspend fun mySummary(): ProgressSummary

    /** The `PENDING` items whose prerequisites are all passed. The rest of `PENDING` is blocked. */
    @GET("api/semaphore/me/eligible")
    suspend fun myEligible(): List<PensumCourse>

    @GET("api/catalog/pensums/{pensumCode}")
    suspend fun pensum(@Path("pensumCode") pensumCode: String): Pensum

    /** [period] `null` is the current one. */
    @GET("api/catalog/pensums/{pensumCode}/electives")
    suspend fun electives(
        @Path("pensumCode") pensumCode: String,
        @Query("period") period: String? = null,
    ): List<ElectiveOffering>

    @GET("api/semaphore/me/plans")
    suspend fun myPlans(): List<AcademicPlan>

    @POST("api/semaphore/me/plans")
    suspend fun createPlan(@Body request: AcademicPlanRequest): AcademicPlan

    @DELETE("api/semaphore/me/plans/{planId}")
    suspend fun deletePlan(@Path("planId") planId: String): Response<Unit>

    @PUT("api/semaphore/me/plans/{planId}/placements/{pensumItemCode}")
    suspend fun place(
        @Path("planId") planId: String,
        @Path("pensumItemCode") pensumItemCode: String,
        @Body request: PlacementRequest,
    ): AcademicPlan

    /** Back to the level the pensum gives it. `404` when it was never moved. */
    @DELETE("api/semaphore/me/plans/{planId}/placements/{pensumItemCode}")
    suspend fun resetPlacement(
        @Path("planId") planId: String,
        @Path("pensumItemCode") pensumItemCode: String,
    ): AcademicPlan
}
