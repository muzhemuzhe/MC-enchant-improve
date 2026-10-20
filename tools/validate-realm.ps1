param([ValidateSet('build','server','client','compile','restore')][string]$Stage='build',[string]$Log='realm-validation')
$ErrorActionPreference='Stop'
$env:JAVA_HOME=(Resolve-Path "$PSScriptRoot/../.tools/jdk-25.0.4.1+1").Path
$env:Path="$env:JAVA_HOME/bin;$env:Path"
$tasks=@(switch($Stage){'build'{@('build')};'server'{@('runGametest','-Prealm')};'client'{@('runClientGametest','-Prealm')};'compile'{@('compileGametestJava')};'restore'{@('assemble','processGametestResources')}})
& "$PSScriptRoot/../gradlew.bat" @tasks --offline *> "$PSScriptRoot/../.tools/$Log.log"
$result=$LASTEXITCODE
Get-Content "$PSScriptRoot/../.tools/$Log.log" -Tail 65
exit $result
