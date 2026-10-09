# Clean session-expiry navigation - 9 October 2026

The user reported that repeated `/?expired=1` addresses looked broken. The invalid-session strategy now redirects to `/` and creates a fresh anonymous session before redirecting so stale cookies on uncached paths do not cause repeated invalid-session navigation. Protected navigational destinations remain cached for login; API/background/non-GET requests still receive 401 without HTML redirects or a newly created session.

Legacy `/?expired=1` and `/login?expired=1` links redirect to the clean homepage. Removed the unreachable expiry notice from the login template. Session timeouts, authentication and authorization remain in force.

Verification: 14 tests across CustomInvalidSessionStrategyTest (3) and LoginIntegrationTest (11), zero failures/errors/skips. Executable package built successfully. A first regression assertion incorrectly assumed Spring's saved URL lacked its existing `?continue` marker; corrected it to verify the protected destination path without changing the cache behaviour. Final run completed in 39 seconds.

Local server restarted as PID 34912 on loopback port 8081. Opening the old expiry URL returned HTTP 200 at `http://127.0.0.1:8081/`. The existing browser tab with the stale pre-restart session was reloaded and resolved to the clean homepage; a second refresh stayed at `/`, with no expiry links in the rendered page. Screenshot is stored in ignored build/session-expiry-clean-home-20261009.jpg.

Render deployment of the preceding commit remains blocked by the existing 512MB service memory limit. The proposed 2GB/$25-month tier has not been saved or approved. This focused fix does not close the broader V2 acceptance checklist.
