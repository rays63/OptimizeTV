# Reverts everything optimize.ps1 did. Run: powershell -ExecutionPolicy Bypass -File D:\TV\google-tv\undo.ps1
$s = "192.168.1.74:5555"
adb connect $s | Out-Null
Get-Content "$PSScriptRoot\packages-disabled.txt" | Where-Object { $_ } | ForEach-Object { adb -s $s shell pm enable $_ }
adb -s $s shell cmd package install-existing com.google.android.youtube.tv
adb -s $s uninstall tv.local.ytredirect
adb -s $s shell settings put global window_animation_scale 1.0
adb -s $s shell settings put global transition_animation_scale 1.0
adb -s $s shell settings put global animator_duration_scale 1.0
