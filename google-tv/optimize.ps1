# Debloat + speed up TCL Google TV. Reversible with undo.ps1
$s = "192.168.1.74:5555"
adb connect $s | Out-Null
Get-Content "$PSScriptRoot\packages-disabled.txt" | Where-Object { $_ } | ForEach-Object {
    adb -s $s shell am force-stop $_
    adb -s $s shell pm disable-user --user 0 $_
}
# Snappier UI
adb -s $s shell settings put global window_animation_scale 0.5
adb -s $s shell settings put global transition_animation_scale 0.5
adb -s $s shell settings put global animator_duration_scale 0.5
