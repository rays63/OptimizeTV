# Puts back the original NetTV boot logo and boot video.
param([string]$s = "192.168.1.77:5789")
$env:MSYS_NO_PATHCONV = 1
adb connect $s | Out-Null
adb -s $s root | Out-Null; Start-Sleep 3; adb connect $s | Out-Null
adb -s $s shell rm -f /data/local/boot.mp4
adb -s $s push "$PSScriptRoot\..\bootlogo-backup\bootlogo.bmp" /sdcard/bootlogo.bmp
adb -s $s shell "mkdir -p /mnt/bl && mount -t vfat /dev/block/by-name/bootloader /mnt/bl && cp /sdcard/bootlogo.bmp /mnt/bl/bootlogo.bmp && sync && umount /mnt/bl; rm /sdcard/bootlogo.bmp"
