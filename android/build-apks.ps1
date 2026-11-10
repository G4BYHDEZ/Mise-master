param([string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Java\jdk-17' }
$BuildTools = Join-Path $SdkRoot 'build-tools\36.0.0'
$AndroidJar = Join-Path $SdkRoot 'platforms\android-35\android.jar'
$OutRoot = Join-Path $PSScriptRoot 'build'
$DependencyRoot = Join-Path $OutRoot 'dependencies'
$KeyStore = Join-Path $OutRoot 'mise-debug.keystore'
if (!(Test-Path $AndroidJar)) { throw "No se encontró Android SDK en $SdkRoot. Ajusta -SdkRoot." }
if (!(Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) { throw "No se encontró JDK en $JavaHome. Define JAVA_HOME." }
New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null
New-Item -ItemType Directory -Force -Path $DependencyRoot | Out-Null
if (!(Test-Path $KeyStore)) { & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -keystore $KeyStore -storepass android -alias mise -keypass android -dname 'CN=Mise Debug,O=Mise,C=MX' -keyalg RSA -keysize 2048 -validity 10000 | Out-Null }
$Dependencies = @(
  @{Group='com.google.android.gms'; Artifact='play-services-wearable'; Version='19.0.0'; Extension='aar'},
  @{Group='com.google.android.gms'; Artifact='play-services-base'; Version='18.5.0'; Extension='aar'},
  @{Group='com.google.android.gms'; Artifact='play-services-basement'; Version='18.4.0'; Extension='aar'},
  @{Group='com.google.android.gms'; Artifact='play-services-tasks'; Version='18.2.0'; Extension='aar'},
  @{Group='androidx.core'; Artifact='core'; Version='1.2.0'; Extension='aar'},
  @{Group='androidx.collection'; Artifact='collection'; Version='1.0.0'; Extension='jar'}
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$DependencyJars = @()
foreach ($dependency in $Dependencies) {
  $folder = Join-Path $DependencyRoot "$($dependency.Artifact)-$($dependency.Version)"
  New-Item -ItemType Directory -Force -Path $folder | Out-Null
  $filename = "$($dependency.Artifact)-$($dependency.Version).$($dependency.Extension)"
  $download = Join-Path $folder $filename
  if (!(Test-Path $download)) {
    $url = "https://dl.google.com/dl/android/maven2/$($dependency.Group.Replace('.','/'))/$($dependency.Artifact)/$($dependency.Version)/$filename"
    Invoke-WebRequest -Uri $url -OutFile $download
  }
  if ($dependency.Extension -eq 'aar') {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($download)
    try {
      $classes = $archive.GetEntry('classes.jar')
      if (!$classes) { throw "No se encontró classes.jar en $download" }
      $stream = $classes.Open()
      $output = [System.IO.File]::Create((Join-Path $folder 'classes.jar'))
      try { $stream.CopyTo($output) } finally { $output.Dispose(); $stream.Dispose() }
    } finally { $archive.Dispose() }
    $DependencyJars += Join-Path $folder 'classes.jar'
  } else {
    $DependencyJars += $download
  }
}
$Work = Join-Path $OutRoot 'work'
if (Test-Path $Work) { Remove-Item -Recurse -Force $Work }
New-Item -ItemType Directory -Force -Path $Work | Out-Null
foreach ($variant in @(@{Name='mobile'},@{Name='wear'})) {
  $v = $variant.Name; $stage=Join-Path $Work $v; $classes=Join-Path $stage 'classes'; $dex=Join-Path $stage 'dex'; New-Item -ItemType Directory -Force -Path $classes,$dex | Out-Null
  $compileClasspath = (@($AndroidJar) + $DependencyJars) -join [IO.Path]::PathSeparator
  & (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath $compileClasspath -d $classes (Join-Path $PSScriptRoot 'MiseActivity.java') (Join-Path $PSScriptRoot 'RecipeSyncService.java')
  if ($LASTEXITCODE -ne 0) { throw "javac falló para $v" }
  $classFiles = @(Get-ChildItem $classes -Filter '*.class' -Recurse | ForEach-Object { $_.FullName })
  & (Join-Path $BuildTools 'd8.bat') --min-api 26 --lib $AndroidJar --output $dex @classFiles @DependencyJars
  if ($LASTEXITCODE -ne 0) { throw "D8 falló para $v" }
  $unsigned=Join-Path $stage "$v-unsigned.apk"; $aligned=Join-Path $stage "$v-aligned.apk"; $apk=Join-Path $ProjectRoot "mise-$v.apk"
  & (Join-Path $BuildTools 'aapt.exe') package -f -M (Join-Path $PSScriptRoot "$v\AndroidManifest.xml") -S (Join-Path $PSScriptRoot 'res') -I $AndroidJar -F (Join-Path $stage 'resources.ap_')
  if ($LASTEXITCODE -ne 0) { throw "AAPT falló para $v" }
  Copy-Item (Join-Path $stage 'resources.ap_') $unsigned
  $dexFiles = @(Get-ChildItem $dex -Filter 'classes*.dex' | ForEach-Object { $_.Name })
  if ($dexFiles.Count -eq 0) { throw "D8 no generó archivos DEX para $v" }
  Push-Location $dex; try { & (Join-Path $JavaHome 'bin\jar.exe') uf $unsigned @dexFiles; if ($LASTEXITCODE -ne 0) { throw 'No se pudo añadir DEX al APK' } } finally { Pop-Location }
  & (Join-Path $BuildTools 'zipalign.exe') -f 4 $unsigned $aligned
  if ($LASTEXITCODE -ne 0) { throw "zipalign falló para $v" }
  & (Join-Path $BuildTools 'apksigner.bat') sign --ks $KeyStore --ks-pass pass:android --key-pass pass:android --out $apk $aligned
  if ($LASTEXITCODE -ne 0) { throw "apksigner falló para $v" }
  & (Join-Path $BuildTools 'apksigner.bat') verify --verbose $apk
  if ($LASTEXITCODE -ne 0) { throw "La verificación de firma falló para $v" }
  Write-Host "Creado: $apk"
}
