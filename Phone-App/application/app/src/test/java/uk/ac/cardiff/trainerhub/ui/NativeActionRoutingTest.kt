package uk.ac.cardiff.trainerhub.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.ac.cardiff.trainerhub.data.remote.MobileRole

class NativeActionRoutingTest {
    @Test
    fun operationalActionsReachTheirRoleDestinations() {
        assertEquals("trainers", routeForAction("Review trainers", MobileRole.GYM_ADMIN))
        assertEquals("requests", routeForAction("Handle requests", MobileRole.GYM_ADMIN))
        assertEquals("requests", routeForAction("View gym application", MobileRole.GYM_ADMIN))
        assertEquals("more", routeForAction("View account", MobileRole.GYM_ADMIN))
        assertEquals("clients", routeForAction("Review clients", MobileRole.TRAINER))
        assertEquals("calendar", routeForAction("Plan sessions", MobileRole.TRAINER))
    }

    @Test
    fun clientActionsDistinguishTrainingFromTheAutomatedHelper() {
        assertEquals("day", routeForAction("Open day view", MobileRole.CLIENT))
        assertEquals("train", routeForAction("Log training", MobileRole.CLIENT))
        assertEquals("calendar", routeForAction("Open calendar", MobileRole.CLIENT))
        assertEquals("chat", routeForAction("Open Charlie helper", MobileRole.CLIENT))
    }
}
