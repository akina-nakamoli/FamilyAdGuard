param([string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk", [string]$Jdk = 'C:/Program Files/Java/jdk-17')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (!(Test-Path "$Sdk/platforms/android-34/android.jar")) { $Sdk = "$env:USERPROFILE/AppData/Local/Android/Sdk" }
$env:JAVA_HOME = $Jdk
$bt = Join-Path $Sdk 'build-tools/34.0.0'
$android = Join-Path $Sdk 'platforms/android-34/android.jar'
function Check { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
New-Item -ItemType Directory -Force build/classes,build/dex,build/test,dist | Out-Null
$sources = @(Get-ChildItem app/src -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$Jdk/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $android -d build/classes @sources
Check
& "$Jdk/bin/javac.exe" -encoding UTF-8 -d build/test app/src/cn/family/adguard/Dns.java app/src/cn/family/adguard/Trace.java app/src/cn/family/adguard/Rules.java tests/CoreTest.java tests/TraceTest.java tests/RulesTest.java
Check
& "$Jdk/bin/java.exe" -cp build/test cn.family.adguard.CoreTest
Check
& "$Jdk/bin/java.exe" -cp build/test cn.family.adguard.TraceTest
Check
& "$Jdk/bin/java.exe" -cp build/test cn.family.adguard.RulesTest
Check
$classes = @(Get-ChildItem build/classes -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$bt/d8.bat" --lib $android --min-api 26 --output build/dex @classes
Check
& "$bt/aapt.exe" package -f -M app/AndroidManifest.xml -S app/res -A app/assets -I $android -F build/unsigned.apk
Check
Push-Location build/dex
try { & "$bt/aapt.exe" add ../unsigned.apk classes.dex; Check } finally { Pop-Location }
& "$bt/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
Check
if (!(Test-Path build/local-test.jks)) {
 & "$Jdk/bin/keytool.exe" -genkeypair -keystore build/local-test.jks -storepass android -keypass android -alias localtest -dname 'CN=Family Ad Guard Local Test' -keyalg RSA -keysize 2048 -validity 3650
 Check
}
& "$bt/apksigner.bat" sign --ks build/local-test.jks --ks-pass pass:android --key-pass pass:android --out dist/FamilyAdGuard.apk build/aligned.apk
Check
& "$bt/apksigner.bat" verify --verbose dist/FamilyAdGuard.apk
Check
Write-Host 'Built dist/FamilyAdGuard.apk (local test signing key, not for store publication)'
