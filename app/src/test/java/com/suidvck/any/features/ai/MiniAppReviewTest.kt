package com.suidvck.any.features.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppReviewTest {
    private val validPlan = """{
        "app_name":"Notes", "description":"Personal notes",
        "features":["Create","Search"], "technologies":["HTML","CSS","JavaScript"],
        "libraries":[], "external_assets":[], "external_services":[],
        "license_review":{"status":"no_external_libraries","details":[]},
        "risks":[], "requires_user_approval":true
    }"""

    @Test fun planMustExplicitlyRequireApproval() {
        assertNotNull(MiniAppPlan.parse(validPlan))
        assertNull(MiniAppPlan.parse(validPlan.replace("true", "false")))
        assertNull(MiniAppPlan.parse("not json"))
    }

    @Test fun unknownLicenseIsPreservedForReview() {
        val plan = MiniAppPlan.parse(validPlan.replace("no_external_libraries", "ยังไม่สามารถตรวจสอบได้"))
        assertEquals("ยังไม่สามารถตรวจสอบได้", plan?.licenseStatus)
        assertTrue(plan!!.toReviewText().contains("not independently verified"))
    }

    @Test fun detectsUnapprovedNetworkOriginsAndAllowsDeclaredOrigins() {
        val plan = MiniAppPlan.parse(validPlan)!!
        val html = "<html><script src=\"https://cdn.example/lib.js\"></script></html>"
        assertEquals(listOf("cdn.example"), MiniAppPlanValidator.unapprovedOrigins(html, plan))
        val approved = plan.copy(libraries = listOf("Library docs: https://cdn.example/lib.js (MIT)"))
        assertTrue(MiniAppPlanValidator.unapprovedOrigins(html, approved).isEmpty())
    }

    @Test fun selfContainedHtmlPassesWithoutExternalOrigins() {
        assertFalse(MiniAppPlanValidator.unapprovedOrigins("<html><style>body{}</style></html>", MiniAppPlan.parse(validPlan)!!).isNotEmpty())
    }

    @Test fun generationRequiresExplicitApprovalAndGrantIsOneUse() {
        val plan = MiniAppPlan.parse(validPlan)!!
        val gate = MiniAppApprovalGate()
        gate.present(plan)
        assertNull(gate.approve(plan.copy(appName = "Different")))
        val grant = gate.approve(plan)!!
        assertEquals(plan, gate.consume(grant))
        assertNull(gate.consume(grant))
    }

    @Test fun modifyAndCancelInvalidateEarlierApproval() {
        val plan = MiniAppPlan.parse(validPlan)!!
        val gate = MiniAppApprovalGate()
        gate.present(plan)
        val oldGrant = gate.approve(plan)!!
        gate.present(plan.copy(externalServices = listOf("https://api.example")))
        assertNull(gate.consume(oldGrant))
        gate.cancel()
        assertNull(gate.approve(plan))
    }

    @Test fun newlyDetectedLibraryOriginNeedsItsOwnPlanApproval() {
        val plan = MiniAppPlan.parse(validPlan)!!
        val gate = MiniAppApprovalGate()
        gate.present(plan)
        val firstApproval = gate.approve(plan)!!
        val generated = "<script src=\"https://cdn.example/library.js\"></script>"
        assertEquals(listOf("cdn.example"), MiniAppPlanValidator.unapprovedOrigins(generated, plan))
        val revised = plan.copy(libraries = listOf("Library from https://cdn.example/library.js; license unverified"))
        gate.present(revised)
        assertNull(gate.consume(firstApproval))
        assertTrue(MiniAppPlanValidator.unapprovedOrigins(generated, revised).isEmpty())
        assertNotNull(gate.approve(revised))
    }
}
