﻿# Local HTTP + PostgreSQL smoke test. Requires isolated DB and mock backend.
param(
    [string]$BaseUrl = 'http://localhost:18080',
    [string]$Container = 'newvent-postgres',
    [string]$Database = 'newvent_filtering_it_20261001'
)
$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^http://localhost:18080$' -or $Database -notmatch '^newvent_filtering_it_') {
    throw 'Only the isolated local test environment is supported.'
}
$script:checks = 0
$script:headers = @{}
$dbInfo = (docker inspect $Container | ConvertFrom-Json)[0]
$dbUser = ($dbInfo.Config.Env | Where-Object { $_ -like 'POSTGRES_USER=*' }) -replace '^POSTGRES_USER=', ''

function Assert-That($condition, [string]$label) {
    if (-not $condition) { throw "FAIL: $label" }
    $script:checks++
    Write-Output "PASS: $label"
}
function Api([string]$method, [string]$path, $payload, [int]$expected = 200) {
    $args = @{ Uri = "$BaseUrl$path"; Method = $method; Headers = $script:headers; UseBasicParsing = $true; TimeoutSec = 15 }
    if ($null -ne $payload) {
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = [Text.Encoding]::UTF8.GetBytes(($payload | ConvertTo-Json -Depth 10 -Compress))
    }
    try {
        $r = Invoke-WebRequest @args
        $status = [int]$r.StatusCode
        $body = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    if ($status -ne $expected) { throw "$method $path expected $expected, got $status : $body" }
    if ($body) { return ($body | ConvertFrom-Json) }
}
function Sql([string]$query) {
    $r = docker exec $Container psql -U $dbUser -d $Database -At -v ON_ERROR_STOP=1 -c $query
    if ($LASTEXITCODE -ne 0) { throw 'SQL check failed' }
    return (($r -join '').Trim())
}
function Counts([long]$eventId) {
    return (Sql "SELECT (SELECT count(*) FROM event_versions WHERE event_id=$eventId)||':'||(SELECT count(*) FROM llm_call_logs WHERE event_id=$eventId)")
}
function New-Event {
    $r = Api POST '/api/admin/events' @{
        name = 'Filtering integration'; startAt = '2026-10-01T00:00:00+09:00'
        endAt = '2026-12-31T23:59:59+09:00'; grade = 'NORMAL'
    } 201
    return [long]$r.data.id
}
function Wait-Job([long]$eventId, [string]$jobId) {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $r = Api GET "/api/admin/events/$eventId/generate/$jobId" $null
        if ($r.data.done) { return $r.data }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Job timed out: $jobId"
}
function Start-Job([long]$eventId, [string]$flow, $payload) {
    $r = Api POST "/api/admin/events/$eventId/$flow" $payload 202
    return (Wait-Job $eventId $r.data.jobId)
}

$login = Api POST '/auth/admin/login' @{loginId='admin'; password='admin1234!'}
$script:headers = @{Authorization = "Bearer $($login.data.accessToken)"}
$eventId = New-Event
Assert-That ($eventId -gt 0) 'Real admin login and event creation'
$genText = '여름 이벤트를 만들어줘. 문의 이메일은 event@example.com이야.'
$initial = Counts $eventId
$ask = Start-Job $eventId 'generate' @{requestText=$genText}
Assert-That ($ask.phase -eq 'ASK_BACK' -and $ask.privacyConfirmationRequired -and $ask.privacyTypes -contains 'EMAIL') 'Generation asks for email confirmation'
Assert-That ((Counts $eventId) -eq $initial) 'Before consent: no version and no LLM call log'
$invalid = Api POST "/api/admin/events/$eventId/generate" @{requestText=($genText+' 변경'); privacyConfirmationJobId=$ask.jobId; privacyConfirmed=$true} 409
Assert-That ($invalid.code -eq 'FILTER409-2') 'Changed confirmation request rejected with 409'
$gen = Start-Job $eventId 'generate' @{requestText=$genText; privacyConfirmationJobId=$ask.jobId; privacyConfirmed=$true}
Assert-That ($gen.phase -eq 'DONE' -and $gen.versionId -gt 0) 'Confirmed generation completes with versionId'
Assert-That ((Sql "SELECT count(*) FROM event_versions WHERE event_id=$eventId") -eq '1') 'Generated version persisted in PostgreSQL'
Assert-That ((Sql "SELECT count(*) FROM llm_call_logs WHERE event_id=$eventId AND provider='mock'") -gt 0) 'Actual mock gateway call persisted'
$preview = Api GET "/api/admin/events/$eventId/preview" $null
Assert-That ($preview.data.versionId -eq $gen.versionId -and $preview.data.html.Length -gt 0) 'Preview loads saved version'

$editText = '혜택 문구를 고쳐줘. 문의 이메일은 event@example.com이야.'
$beforeEdit = Counts $eventId
$editAsk = Start-Job $eventId 'edit' @{requestText=$editText}
Assert-That ($editAsk.phase -eq 'ASK_BACK' -and $editAsk.privacyConfirmationRequired) 'Edit asks for privacy confirmation'
Assert-That ((Counts $eventId) -eq $beforeEdit) 'Unconfirmed edit leaves versions and calls unchanged'
$null = Api POST "/api/admin/events/$eventId/edit" @{requestText=$editText; privacyConfirmationJobId=$editAsk.jobId} 409
Assert-That ((Counts $eventId) -eq $beforeEdit) 'Missing explicit consent rejected without writes'
$edited = Start-Job $eventId 'edit' @{requestText=$editText; privacyConfirmationJobId=$editAsk.jobId; privacyConfirmed=$true}
Assert-That ($edited.phase -eq 'DONE' -and $edited.versionId -gt $gen.versionId) 'Confirmed edit completes with new version'
Assert-That ((Sql "SELECT source_version_id FROM event_versions WHERE id=$($edited.versionId) AND event_id=$eventId") -eq "$($gen.versionId)") 'Edited version links to original version'
$preview2 = Api GET "/api/admin/events/$eventId/preview" $null
Assert-That ($preview2.data.versionId -eq $edited.versionId -and $preview2.data.html -match '수정됨') 'Preview displays mock edit from saved version'
$null = Api POST "/api/admin/events/$eventId/edit" @{requestText=$editText; privacyConfirmationJobId=$editAsk.jobId; privacyConfirmed=$true} 409
Assert-That ((Sql "SELECT count(*) FROM event_versions WHERE event_id=$eventId") -eq '2') 'Old base-version confirmation rejected'

foreach ($case in @(
    @{text='혜택 문의 번호를 010-1234-5678로 바꿔줘'; type='PHONE'},
    @{text='혜택 수령지는 서구 서달로 123이야'; type='ADDRESS'},
    @{text='혜택 수령지는 서해구 서달로 123이야'; type='ADDRESS'},
    @{text='혜택 문구에 900101-1234567을 넣어줘'; type='RESIDENT_NUMBER'}
)) {
    $snapshot = Counts $eventId
    $job = Start-Job $eventId 'edit' @{requestText=$case.text}
    Assert-That ($job.phase -eq 'ASK_BACK' -and $job.privacyTypes -contains $case.type -and (Counts $eventId) -eq $snapshot) "Detect $($case.type) before any calls/writes"
}
$ordinary = Start-Job $eventId 'edit' @{requestText='이거 고쳐줘'}
Assert-That ($ordinary.phase -eq 'ASK_BACK' -and -not $ordinary.privacyConfirmationRequired) 'Ordinary clarification remains distinct'
$null = Api POST "/api/admin/events/$eventId/edit" @{requestText=$editText; privacyConfirmationJobId=$ordinary.jobId; privacyConfirmed=$true} 409
Assert-That ($true) 'Ordinary clarification ID cannot confirm privacy'

$otherId = New-Event
$null = Api POST "/api/admin/events/$otherId/generate" @{requestText=$genText; privacyConfirmationJobId=$ask.jobId; privacyConfirmed=$true} 409
Assert-That ((Counts $otherId) -eq '0:0') 'Confirmation cannot cross events'
$tooLong = '가' * 501
$null = Api POST "/api/admin/events/$eventId/edit" @{requestText=$tooLong} 400
Assert-That ($true) 'Existing 501-character limit remains enforced'

# A rejected/failed LLM edit must leave the last successful version available.
$versionCount = Sql "SELECT count(*) FROM event_versions WHERE event_id=$eventId"
$failed = Start-Job $eventId 'edit' @{requestText='혜택 문구를 바꿔줘 FAIL'}
Assert-That ($failed.phase -eq 'FAILED') 'Forced mock validation failure terminates'
$afterFailure = Api GET "/api/admin/events/$eventId/preview" $null
Assert-That ($afterFailure.data.versionId -eq $edited.versionId -and (Sql "SELECT count(*) FROM event_versions WHERE event_id=$eventId") -eq $versionCount) 'Failure preserves last successful version'
Write-Output "TOTAL: $script:checks passed; events=$eventId,$otherId; database=$Database"
