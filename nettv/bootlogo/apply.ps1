# Replaces the NetTV boot logo (bootloader partition) and boot video with the Google TV-style glow (render.py, make-video.ps1). Needs `adb root` (userdebug build).
# Originals are in ..\bootlogo-backup (bootlogo.bmp, boot.mp4, and a full bootloader.img). Undo with restore.ps1.
param([string]$s = "192.168.1.77:5789")
$env:MSYS_NO_PATHCONV = 1
adb connect $s | Out-Null
adb -s $s root | Out-Null; Start-Sleep 3; adb connect $s | Out-Null
# The boot player prefers /data/local/boot.mp4 over /system/media/boot.mp4
adb -s $s push "$PSScriptRoot\gtv-boot.mp4" /data/local/boot.mp4
adb -s $s shell chmod 644 /data/local/boot.mp4
# Same-size 1280x720 32-bit BMP, overwritten in place on the FAT partition
adb -s $s push "$PSScriptRoot\gtv-bootlogo.bmp" /sdcard/bootlogo.bmp
adb -s $s shell "mkdir -p /mnt/bl && mount -t vfat /dev/block/by-name/bootloader /mnt/bl && cp /sdcard/bootlogo.bmp /mnt/bl/bootlogo.bmp && sync && umount /mnt/bl; rm /sdcard/bootlogo.bmp"
