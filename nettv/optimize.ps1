# Debloat NetTV box (NETTV-1000): our home screen replaces the ad launcher, plus SmartTube. Reversible with undo.ps1
# The home screen app is shared with the Google TV: ..\launcher (build with build.sh). Boot logo: bootlogo\apply.ps1
# The NetTV launcher stays enabled (not as home): Live TV fails with error 1417 without it.
# The box's wireless-debugging port changes after reboot: find it with `adb mdns services`.
param([string]$s = "192.168.1.77:5789")
adb connect $s | Out-Null
adb -s $s install -r "$PSScriptRoot\..\launcher\launcher.apk"
adb -s $s install -r "$PSScriptRoot\apps\smarttube.apk"
# LocalSend and Stremio: split APKs copied from the Google TV
# (Netflix TV app fails with error -13 here: the box isn't Netflix-certified)
adb -s $s install-multiple -r (Get-ChildItem "$PSScriptRoot\apps\localsend\*.apk").FullName
adb -s $s install-multiple -r (Get-ChildItem "$PSScriptRoot\apps\stremio\*.apk").FullName
# Aurora Store (Play Store client, from F-Droid): the box has no Google Play Services, so no real Play Store
adb -s $s install -r "$PSScriptRoot\apps\aurora\aurora-store-4.8.4.apk"
adb -s $s shell appops set com.aurora.store REQUEST_INSTALL_PACKAGES allow
# Stock YouTube shows ads; SmartTube replaces it
adb -s $s uninstall com.google.android.youtube.tv
# Let LocalSend and the file manager open received APKs for installing
adb -s $s shell appops set org.localsend.localsend_app REQUEST_INSTALL_PACKAGES allow
adb -s $s shell appops set com.softwinner.TvdFileManager REQUEST_INSTALL_PACKAGES allow
Get-Content "$PSScriptRoot\packages-disabled.txt" | Where-Object { $_ } | ForEach-Object {
    adb -s $s shell am force-stop $_
    adb -s $s shell pm disable-user --user 0 $_
}
adb -s $s shell cmd package set-home-activity tv.local.ytredirect/.HomeActivity
# Remote helper: the YouTube button (F11) opens SmartTube
adb -s $s shell settings put secure enabled_accessibility_services tv.local.ytredirect/tv.local.ytredirect.RedirectService
adb -s $s shell settings put secure accessibility_enabled 1
# Snappier UI
adb -s $s shell settings put global window_animation_scale 0.5
adb -s $s shell settings put global transition_animation_scale 0.5
adb -s $s shell settings put global animator_duration_scale 0.5
adb -s $s shell input keyevent KEYCODE_HOME
