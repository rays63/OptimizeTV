# Encodes frames\frame_*.png (from render.py) into gtv-boot.mp4 with Windows' built-in encoder.
# This box's boot player plays the whole video before Android can show the home screen,
# and the video starts ~16.6 s in with Android ready at ~20 s: so keep it ~4 s or boot gets slower.
param([double]$seconds = 4, [int]$fps = 15)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Editing.MediaComposition, Windows.Media.Editing, ContentType = WindowsRuntime]
$null = [Windows.Media.Editing.MediaClip, Windows.Media.Editing, ContentType = WindowsRuntime]
$null = [Windows.Storage.StorageFolder, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Media.MediaProperties.MediaEncodingProfile, Windows.Media.MediaProperties, ContentType = WindowsRuntime]
$m = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 }
$asTask = $m | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
$asTaskP = $m | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperationWithProgress`2' } | Select-Object -First 1
function Await($op, [Type]$t) { $task = $asTask.MakeGenericMethod($t).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }
function AwaitP($op, [Type]$t, [Type]$p) { $task = $asTaskP.MakeGenericMethod($t, $p).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }

$frameDur = [TimeSpan]::FromTicks([long](10000000 / $fps))
$frames = Get-ChildItem "$PSScriptRoot\frames\frame_*.png" | Sort-Object Name | ForEach-Object {
    Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($_.FullName)) ([Windows.Storage.StorageFile])
}
$comp = New-Object Windows.Media.Editing.MediaComposition
$add = [System.Collections.Generic.ICollection[Windows.Media.Editing.MediaClip]].GetMethod('Add')
for ($i = 0; $i -lt [int]($seconds * $fps); $i++) {
    $f = $frames[$i % $frames.Count]
    $clip = Await ([Windows.Media.Editing.MediaClip]::CreateFromImageFileAsync($f, $frameDur)) ([Windows.Media.Editing.MediaClip])
    $add.Invoke($comp.Clips, @($clip)) | Out-Null
}
$folder = Await ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync($PSScriptRoot)) ([Windows.Storage.StorageFolder])
$file = Await ($folder.CreateFileAsync('gtv-boot.mp4', [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])
$prof = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateMp4([Windows.Media.MediaProperties.VideoEncodingQuality]::HD720p)
$prof.Audio = $null
$prof.Video.FrameRate.Numerator = $fps
$prof.Video.FrameRate.Denominator = 1
$prof.Video.Bitrate = 2000000
$r = AwaitP ($comp.RenderToFileAsync($file, [Windows.Media.Editing.MediaTrimmingPreference]::Precise, $prof)) ([Windows.Media.Transcoding.TranscodeFailureReason]) ([double])
"result: $r, $((Get-Item "$PSScriptRoot\gtv-boot.mp4").Length) bytes"
