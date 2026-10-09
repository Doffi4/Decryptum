[CmdletBinding()]
param(
    [ValidateSet('dry', 'live', 'score')][string]$Mode = 'dry',
    [string]$Cases,
    [string]$Model,
    [ValidateSet('en', 'ru', 'en,ru', 'ru,en')][string]$Languages = 'en,ru',
    [ValidateRange(1, 3)][int]$Repeats = 1,
    [ValidateRange(1, 180)][int]$MaxRequests = 2,
    [string]$RunId,
    [ValidateSet('aggregate-v1', 'aggregate-v2-candidate')][string]$PromptVersion = 'aggregate-v2-candidate'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
if ($Mode -eq 'live' -and [string]::IsNullOrWhiteSpace($Cases)) {
    throw 'Live mode requires -Cases S01 (smoke test) or an explicit larger selection.'
}
if ($Mode -eq 'score' -and $RunId -notmatch '^[A-Za-z0-9_-]{1,100}$') {
    throw 'Score mode requires -RunId with the output directory name, not a path.'
}
if ($Cases -and $Cases -notmatch '^(all|S[0-9]{2}(,S[0-9]{2})*)$') {
    throw 'Cases must be all or comma-separated synthetic case IDs.'
}
$previousKey = [Environment]::GetEnvironmentVariable('DECRYPTUM_ADVISOR_EVAL_KEY', 'Process')
$previousModel = [Environment]::GetEnvironmentVariable('DECRYPTUM_ADVISOR_EVAL_MODEL', 'Process')
Push-Location -LiteralPath $repositoryRoot
try {
    if ($Mode -eq 'live') {
        if ([string]::IsNullOrWhiteSpace($Model)) { $Model = $previousModel }
        if ([string]::IsNullOrWhiteSpace($Model) -or $Model -notmatch '^[A-Za-z0-9._-]{1,100}$') {
            throw 'Live mode requires -Model with an actual structured-output-capable model ID.'
        }
        [Environment]::SetEnvironmentVariable('DECRYPTUM_ADVISOR_EVAL_MODEL', $Model, 'Process')
        if ([string]::IsNullOrWhiteSpace($previousKey)) {
            $secret = Read-Host 'Disposable Claude API key (masked; not saved)' -AsSecureString
            $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
            try {
                [Environment]::SetEnvironmentVariable('DECRYPTUM_ADVISOR_EVAL_KEY', [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer), 'Process')
            } finally {
                [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
                $secret.Dispose()
            }
        }
    }
    # A fresh build destination avoids Windows locks without deleting prior results.
    $invocationId = 'build-' + [Guid]::NewGuid().ToString('N')
    $buildRoot = Join-Path $repositoryRoot ".artifacts/advisor-eval/builds/$invocationId"
    New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
    $initScript = Join-Path $buildRoot 'output.init.gradle'
    $initText = 'allprojects { layout.buildDirectory.set(file("${rootDir}/.artifacts/advisor-eval/builds/INVOCATION/${project.name}")) }'.Replace('INVOCATION', $invocationId)
    [IO.File]::WriteAllText($initScript, $initText, [Text.UTF8Encoding]::new($false))
    $gradleArguments = @('-I', ".artifacts/advisor-eval/builds/$invocationId/output.init.gradle",
        ':advisor-evaluation:runAdvisorEvaluation', "-PadvisorEvalMode=$Mode",
        "-PadvisorEvalLanguages=$Languages", "-PadvisorEvalRepeats=$Repeats",
        "-PadvisorEvalMaxRequests=$MaxRequests", "-PadvisorEvalPrompt=$PromptVersion", '--console=plain')
    if ($Cases) { $gradleArguments += "-PadvisorEvalCases=$Cases" }
    if ($RunId) { $gradleArguments += "-PadvisorEvalRun=$RunId" }
    & .\gradlew.bat @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw 'Advisor evaluation command failed. No automatic retry was attempted.' }
} finally {
    [Environment]::SetEnvironmentVariable('DECRYPTUM_ADVISOR_EVAL_KEY', $previousKey, 'Process')
    [Environment]::SetEnvironmentVariable('DECRYPTUM_ADVISOR_EVAL_MODEL', $previousModel, 'Process')
    Pop-Location
}
