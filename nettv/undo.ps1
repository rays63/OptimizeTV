# Reverts everything optimize.ps1 did. Run: powershell -ExecutionPolicy Bypass -File D:\TV\nettv\undo.ps1
param([string]$s = "192.168.1.77:5789")
adb connect $s | Out-Null
Get-Content "$PSScriptRoot\packages-disabled.txt" | Where-Object { $_ } | ForEach-Object { adb -s $s shell pm enable $_ }
adb -s $s shell cmd package set-home-activity np.com.geniussystems.android.stb.launcher/.MainActivity
adb -s $s shell settings delete secure enabled_accessibility_services
adb -s $s uninstall tv.local.ytredirect
adb -s $s uninstall org.smarttube.stable
adb -s $s uninstall org.localsend.localsend_app
adb -s $s uninstall com.stremio.one
adb -s $s uninstall com.aurora.store
adb -s $s shell settings put global window_animation_scale 1.0
adb -s $s shell settings put global transition_animation_scale 1.0
adb -s $s shell settings put global animator_duration_scale 1.0
