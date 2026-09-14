param([switch]$Logs)
$ErrorActionPreference = 'Stop'
# ponytail: un solo comando para probar sin abrir Android Studio
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$env:Path = "$env:JAVA_HOME\bin;$Sdk\platform-tools;$env:Path"
$Adb = "$Sdk\platform-tools\adb.exe"

& "$PSScriptRoot\gradlew.bat" :app:assembleDebug --parallel
& $Adb install -r "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
& $Adb shell monkey -p com.defitracker.app -c android.intent.category.LAUNCHER 1
if ($Logs) { & $Adb logcat -c; & $Adb logcat --pid=$(& $Adb shell pidof com.defitracker.app) }
