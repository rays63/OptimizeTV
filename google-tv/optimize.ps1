# Debloat + speed up TCL Google TV. Reversible with undo.ps1
$s = "192.168.1.74:5555"
adb connect $s | Out-Null
Get-Content "$PSScriptRoot\packages-disabled.txt" | Where-Object { $_ } | ForEach-Object {
    adb -s $s shell am force-stop $_
    adb -s $s shell pm disable-user --user 0 $_
}
# Usage access for the home screen (Continue row, recent-apps switcher), if it's installed
adb -s $s shell appops set tv.local.ytredirect GET_USAGE_STATS allow
# Home screen: Google TV's launcher (and its setup "recovery" home) always win over ours, so disable
# them; ours is then the only home, starts at boot and isn't killed for memory. undo.ps1 re-enables them.
adb -s $s shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s $s shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb -s $s shell cmd package set-home-activity tv.local.ytredirect/.HomeActivity
# Remote helper (YouTube/Prime/TCL Channel buttons). The TV switches it off on reboot, so let Home
# switch it back on (WRITE_SECURE_SETTINGS), and enable it now.
adb -s $s shell pm grant tv.local.ytredirect android.permission.WRITE_SECURE_SETTINGS
adb -s $s shell settings put secure enabled_accessibility_services tv.local.ytredirect/tv.local.ytredirect.RedirectService
adb -s $s shell settings put secure accessibility_enabled 1
# Snappier UI
adb -s $s shell settings put global window_animation_scale 0.5
adb -s $s shell settings put global transition_animation_scale 0.5
adb -s $s shell settings put global animator_duration_scale 0.5
