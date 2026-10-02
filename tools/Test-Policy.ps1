param([string]$DependencyDirectory)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path $PSScriptRoot -Parent
$taskClasses = Join-Path $taskProject 'work\policy-test\classes'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
if (-not $DependencyDirectory) {
    $DependencyDirectory = Join-Path $taskProject 'work\policy-test\deps'
    New-Item -ItemType Directory -Path $DependencyDirectory -Force | Out-Null
    $taskArtifacts = @(
        @{ File='junit-4.13.2.jar'; Url='https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar' },
        @{ File='hamcrest-core-1.3.jar'; Url='https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar' }
    )
    foreach ($taskArtifact in $taskArtifacts) {
        $taskDestination = Join-Path $DependencyDirectory $taskArtifact.File
        if (-not (Test-Path -LiteralPath $taskDestination)) {
            Invoke-WebRequest -Uri $taskArtifact.Url -OutFile $taskDestination
        }
    }
}
$taskJavac = Get-Command javac -ErrorAction SilentlyContinue
$taskJava = Get-Command java -ErrorAction SilentlyContinue
if (-not $taskJavac -or -not $taskJava) { throw 'Install a Java JDK and put java and javac on PATH.' }
$taskClasspath = (Join-Path $DependencyDirectory 'junit-4.13.2.jar') + [IO.Path]::PathSeparator + (Join-Path $DependencyDirectory 'hamcrest-core-1.3.jar')
$taskPolicy = Join-Path $taskProject 'app\src\main\java\dev\local\timoreply\ReplyPolicy.java'
$taskTest = Join-Path $taskProject 'app\src\test\java\dev\local\timoreply\ReplyPolicyTest.java'
& $taskJavac.Source --release 17 -cp $taskClasspath -d $taskClasses $taskPolicy $taskTest
if ($LASTEXITCODE -ne 0) { throw 'Policy tests did not compile.' }
& $taskJava.Source -cp ($taskClasses + [IO.Path]::PathSeparator + $taskClasspath) org.junit.runner.JUnitCore dev.local.timoreply.ReplyPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Policy tests failed.' }
