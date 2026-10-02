param(
    [string]$AdbPath = 'adb',
    [string]$Serial,
    [switch]$Watch
)
$ErrorActionPreference = 'Stop'
$taskAdb = Get-Command $AdbPath -ErrorAction SilentlyContinue
if (-not $taskAdb) { throw 'ADB not found. Pass -AdbPath with the full path to adb.exe.' }
$taskDevices = & $taskAdb.Source devices
if ($LASTEXITCODE -ne 0) { throw 'ADB could not list devices.' }
$taskConnected = @($taskDevices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if (-not $Serial) {
    if ($taskConnected.Count -ne 1) { throw 'Connect one phone, unlock it, approve USB debugging, or pass -Serial.' }
    $Serial = $taskConnected[0]
}
if ($Serial -notin $taskConnected) { throw 'Selected phone is not connected and authorized.' }
$taskArgs = @('-s', $Serial)
Write-Output ('Phone: ' + ((& $taskAdb.Source @taskArgs shell getprop ro.product.model) -join '').Trim())
Write-Output ('Android: ' + ((& $taskAdb.Source @taskArgs shell getprop ro.build.version.release) -join '').Trim())
$taskPackage = & $taskAdb.Source @taskArgs shell pm list packages com.hwsj.club
Write-Output ('Timo installed: ' + [bool]($taskPackage -match '^package:com\.hwsj\.club$'))
$taskBot = & $taskAdb.Source @taskArgs shell pm list packages dev.local.timoreply
Write-Output ('Timo Reply installed: ' + [bool]($taskBot -match '^package:dev\.local\.timoreply$'))
$taskListeners = & $taskAdb.Source @taskArgs shell settings get secure enabled_notification_listeners
Write-Output ('Timo Reply notification access: ' + [bool](($taskListeners -join '') -match 'dev\.local\.timoreply/'))
if ($Watch) {
    Write-Output 'Watching bot diagnostics. Receive a NEW Timo message. Press Ctrl+C to stop.'
    & $taskAdb.Source @taskArgs logcat -T 1 -v time 'TimoReply:I' '*:S'
} else {
    Write-Output 'Recent bot diagnostics:'
    & $taskAdb.Source @taskArgs logcat -d -t 1000 -v time 'TimoReply:I' '*:S'
}
