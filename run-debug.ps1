param([switch]$Logs, [string]$Device)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$env:Path = "$env:JAVA_HOME\bin;$Sdk\platform-tools;$env:Path"
$Adb = "$Sdk\platform-tools\adb.exe"

& "$PSScriptRoot\gradlew.bat" :app:assembleDebug --parallel

# sin -s adb revienta cuando ve 2+ dispositivos (ej. celu por WiFi + entrada mDNS duplicada)
if ([string]::IsNullOrWhiteSpace($Device)) { $Device = $env:ANDROID_SERIAL }
if ([string]::IsNullOrWhiteSpace($Device)) {
    $devs = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($devs.Count -eq 0) { throw 'No hay dispositivos conectados (adb devices vacio). Conecta el celu o abre el emulador.' }
    if ($devs.Count -gt 1) { throw ("Hay varios dispositivos, pasa -Device <serial>:`n" + ($devs -join "`n") + "`nEj: ./r -Device " + $devs[0]) }
    $Device = $devs[0]
}
& $Adb -s $Device install -r "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
& $Adb -s $Device shell monkey -p com.defitracker.app -c android.intent.category.LAUNCHER 1
if ($Logs) { & $Adb -s $Device logcat -c; & $Adb -s $Device logcat --pid=$(& $Adb -s $Device shell pidof com.defitracker.app) }
