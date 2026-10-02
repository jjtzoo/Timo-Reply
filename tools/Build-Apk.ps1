param(
    [Parameter(Mandatory)][string]$PlatformDirectory,
    [Parameter(Mandatory)][string]$BuildToolsDirectory,
    [Parameter(Mandatory)][string]$JavaBinDirectory
)
# Optional direct build with official Android SDK tools, without Gradle dependency resolution.
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path $PSScriptRoot -Parent
$taskBuild = Join-Path $taskProject 'work\manual-build'
$taskJar = Join-Path $PlatformDirectory 'android.jar'
$taskGradle = Get-Content -LiteralPath (Join-Path $taskProject 'app\build.gradle') -Raw
$taskVersionCode = [regex]::Match($taskGradle, 'versionCode\s+(\d+)').Groups[1].Value
$taskVersionName = [regex]::Match($taskGradle, "versionName\s+'([^']+)'").Groups[1].Value
if (-not $taskVersionCode -or -not $taskVersionName) { throw 'App version is missing from app/build.gradle' }
$env:JAVA_HOME = Split-Path $JavaBinDirectory -Parent
function Assert-NativeExit([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw ($Step + ' failed with code ' + $LASTEXITCODE) }
}
New-Item -ItemType Directory -Path (Join-Path $taskBuild 'classes'),(Join-Path $taskBuild 'dex') -Force | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskProject 'app\src\main\java') -Filter *.java -Recurse | ForEach-Object { $_.FullName })
& (Join-Path $JavaBinDirectory 'javac.exe') --release 17 -encoding UTF-8 -cp $taskJar -d (Join-Path $taskBuild 'classes') @taskSources
Assert-NativeExit 'Java compilation'
[xml]$taskManifest = Get-Content -LiteralPath (Join-Path $taskProject 'app\src\main\AndroidManifest.xml') -Raw
$taskManifest.manifest.SetAttribute('package','dev.local.timoreply')
$taskManifest.manifest.application.SetAttribute('debuggable','http://schemas.android.com/apk/res/android','true') | Out-Null
$taskManifest.Save((Join-Path $taskBuild 'AndroidManifest.xml'))
& (Join-Path $BuildToolsDirectory 'aapt2.exe') compile --dir (Join-Path $taskProject 'app\src\main\res') -o (Join-Path $taskBuild 'resources.zip')
Assert-NativeExit 'Resource compilation'
& (Join-Path $BuildToolsDirectory 'aapt2.exe') link -o (Join-Path $taskBuild 'unsigned.apk') --manifest (Join-Path $taskBuild 'AndroidManifest.xml') -I $taskJar (Join-Path $taskBuild 'resources.zip') --min-sdk-version 26 --target-sdk-version 36 --version-code $taskVersionCode --version-name $taskVersionName
Assert-NativeExit 'Resource linking'
& (Join-Path $JavaBinDirectory 'jar.exe') --create --file (Join-Path $taskBuild 'classes.jar') -C (Join-Path $taskBuild 'classes') .
Assert-NativeExit 'Class archive'
& (Join-Path $BuildToolsDirectory 'd8.bat') --lib $taskJar --min-api 26 --output (Join-Path $taskBuild 'dex') (Join-Path $taskBuild 'classes.jar')
Assert-NativeExit 'DEX compilation'
& (Join-Path $JavaBinDirectory 'jar.exe') --update --file (Join-Path $taskBuild 'unsigned.apk') -C (Join-Path $taskBuild 'dex') classes.dex
Assert-NativeExit 'DEX packaging'
& (Join-Path $BuildToolsDirectory 'zipalign.exe') -p -f 4 (Join-Path $taskBuild 'unsigned.apk') (Join-Path $taskBuild 'aligned.apk')
Assert-NativeExit 'APK alignment'
$taskKeystore = Join-Path $taskBuild 'debug.keystore'
if (-not (Test-Path -LiteralPath $taskKeystore)) {
    & (Join-Path $JavaBinDirectory 'keytool.exe') -genkeypair -keystore $taskKeystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
    Assert-NativeExit 'Debug key generation'
}
$taskApk = Join-Path $taskBuild 'timo-reply-debug.apk'
& (Join-Path $BuildToolsDirectory 'apksigner.bat') sign --ks $taskKeystore --ks-pass pass:android --key-pass pass:android --out $taskApk (Join-Path $taskBuild 'aligned.apk')
Assert-NativeExit 'APK signing'
& (Join-Path $BuildToolsDirectory 'apksigner.bat') verify --verbose $taskApk
Assert-NativeExit 'APK signature verification'
Write-Output ('APK: ' + $taskApk)
