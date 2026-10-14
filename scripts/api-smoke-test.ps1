# =====================================================
#  Online Shop MVP - API smoke test (full business flow)
#  Owner: Li Xuanwei (backend + database, part D)
#
#  Flow: login -> publish product -> 3 buyers submit intent -> query by code
#        -> duplicate submit blocked -> start trade -> freeze/off-shelf blocked
#        -> confirm-failed (VOID) promote -> confirm-sold -> history -> code invalid
#
#  Prerequisites:
#    1) MySQL is up; the "shop" schema is initialized by schema.sql
#       (or auto-created on application startup).
#    2) Backend is running at http://localhost:8080
#
#  Usage (Windows PowerShell, execution policy restricted -> use Bypass):
#    powershell -ExecutionPolicy Bypass -File .\scripts\api-smoke-test.ps1
#
#  Note: business errors are returned as HTTP 200 with { code, message, data },
#        so Invoke-RestMethod is used (native curl shares arg-quoting issues).
#
#  This file is intentionally ASCII-only so it runs on any PowerShell version.
# =====================================================

$Base = 'http://localhost:8080'
$User = 'seller'
$Pwd  = 'seller123456'

$Global:Pass = 0
$Global:Fail = 0

function Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Body,
        [string]$Token
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$Base$Path"; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 8 -Compress)
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        $msg = $_.ErrorDetails.Message
        if ($msg) { return ($msg | ConvertFrom-Json) }
        throw
    }
}

function Assert {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        Write-Host ("  [PASS] " + $Name) -ForegroundColor Green
        $Global:Pass++
    } else {
        Write-Host ("  [FAIL] " + $Name + "  " + $Detail) -ForegroundColor Red
        $Global:Fail++
    }
}

Write-Host "==== Online Shop MVP  API smoke test ====" -ForegroundColor Cyan

# -----------------------------------------------------
# 1. Seller login (returns token)
# -----------------------------------------------------
Write-Host "[1] Seller login"
$r = Api POST '/api/seller/login' @{ username = $User; password = $Pwd }
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$token = $r.data.token
Assert 'token not empty' ([bool]$token) "token=$token"

# -----------------------------------------------------
# 2. Publish product (status -> ON_SALE 0)
# -----------------------------------------------------
Write-Host "[2] Publish product"
$pname = 'SmokeTestProduct-' + (Get-Date -Format 'yyyyMMddHHmmss')
$r = Api POST '/api/product/publish' @{ name = $pname; description = 'smoke test'; price = '99.90'; requestId = [guid]::NewGuid().ToString() } $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$productId = $r.data.id
Assert 'product is ON_SALE (status 0)' ($r.data.status -eq 0) "status=$($r.data.status)"

# -----------------------------------------------------
# 3. Buyer anonymously views current product (canSubmitIntent flag)
# -----------------------------------------------------
Write-Host "[3] Buyer views current product"
$r = Api GET '/api/buyer/product/current'
Assert 'code=0' ($r.code -eq 0) "code=$($r.code)"
Assert 'canSubmitIntent=true' ($r.data.canSubmitIntent -eq $true) "canSubmitIntent=$($r.data.canSubmitIntent)"

# -----------------------------------------------------
# 4. Three buyers submit intents (FIFO, positions 1/2/3)
# -----------------------------------------------------
Write-Host "[4] Three buyers submit intents"
$codes    = @{}
$ids      = @{}
$expected = @{ 'buyerA' = 1; 'buyerB' = 2; 'buyerC' = 3 }
$buyers   = @(
    @{ label = 'buyerA'; key = 'buyerA'; p = '13800000001' },
    @{ label = 'buyerB'; key = 'buyerB'; p = '13800000002' },
    @{ label = 'buyerC'; key = 'buyerC'; p = '13800000003' }
)
foreach ($b in $buyers) {
    $r = Api POST '/api/buyer/intent' @{ name = $b.key; phone = $b.p; requestId = [guid]::NewGuid().ToString() }
    Assert "submit $($b.label): code=0" ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
    $codes[$b.label] = $r.data.code
    $ids[$b.label]   = $r.data.intent.id
    Assert "$($b.label) position=$($expected[$b.key])" ($r.data.intent.queuePosition -eq $expected[$b.key]) "queuePosition=$($r.data.intent.queuePosition)"
    Assert "$($b.label) got one-time code" ([bool]$r.data.code)
}

# -----------------------------------------------------
# 5. Query intent by code (queuing, canModify/canCancel)
# -----------------------------------------------------
Write-Host "[5] Query intent by code"
$r = Api GET ("/api/buyer/intent?code=" + $codes['buyerA'])
Assert 'code=0' ($r.code -eq 0) "code=$($r.code)"
Assert 'position=1' ($r.data.queuePosition -eq 1) "queuePosition=$($r.data.queuePosition)"
Assert 'canModify=true and canCancel=true' ($r.data.canModify -eq $true -and $r.data.canCancel -eq $true) "canModify=$($r.data.canModify) canCancel=$($r.data.canCancel)"

# -----------------------------------------------------
# 6. Duplicate submit (same phone + same product) -> 40904
# -----------------------------------------------------
Write-Host "[6] Duplicate submit blocked (same phone + product)"
$r = Api POST '/api/buyer/intent' @{ name = 'buyerA'; phone = '13800000001' }
Assert 'code=40904' ($r.code -eq 40904) "code=$($r.code) msg=$($r.message)"

# -----------------------------------------------------
# 7. Start trade (product frozen status 2, head enters trade)
# -----------------------------------------------------
Write-Host "[7] Start trade"
$r = Api POST '/api/product/start-trade' @{ productId = $productId; requestId = [guid]::NewGuid().ToString() } $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
Assert 'product TRADING (status 2)' ($r.data.status -eq 2) "status=$($r.data.status)"
Assert 'head is buyerA' ($r.data.currentIntent.id -eq $ids['buyerA']) "currentIntent=$($r.data.currentIntent.id) buyerA=$($ids['buyerA'])"

# -----------------------------------------------------
# 8. During trade: unfreeze blocked (40908) / off-shelf blocked (40909)
# -----------------------------------------------------
Write-Host "[8] Actions blocked during trade"
$r = Api POST '/api/product/unfreeze' @{ productId = $productId } $token
Assert 'unfreeze during trade -> 40908' ($r.code -eq 40908) "code=$($r.code)"
$r = Api POST '/api/product/off-shelf' @{ productId = $productId } $token
Assert 'off-shelf during trade -> 40909' ($r.code -eq 40909) "code=$($r.code)"

# -----------------------------------------------------
# 9. Confirm failed (VOID) -> buyerA voided, buyerB auto-promoted (product stays TRADING)
# -----------------------------------------------------
Write-Host "[9] Confirm failed (VOID) and promote"
$r = Api POST '/api/product/confirm-failed' @{ productId = $productId; intentId = $ids['buyerA']; disposition = 'VOID'; requestId = [guid]::NewGuid().ToString() } $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
Assert 'product still TRADING (status 2)' ($r.data.status -eq 2) "status=$($r.data.status)"
Assert 'buyerB promoted to head' ($r.data.currentIntent.id -eq $ids['buyerB']) "currentIntent=$($r.data.currentIntent.id) buyerB=$($ids['buyerB'])"

# -----------------------------------------------------
# 10. Confirm sold -> product off-shelf (status 3)
# -----------------------------------------------------
Write-Host "[10] Confirm sold"
$r = Api POST '/api/product/confirm-sold' @{ productId = $productId; intentId = $ids['buyerB']; requestId = [guid]::NewGuid().ToString() } $token
Assert 'sold ok, no active product left' ($r.code -eq 0 -and $null -eq $r.data) "code=$($r.code) data=$($r.data)"

# -----------------------------------------------------
# 11. History products contains this product (status 3)
# -----------------------------------------------------
Write-Host "[11] History products"
$r = Api GET '/api/seller/history/products?page=1&pageSize=10' $null $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code)"
$histIds = @($r.data.list | Where-Object { $_ } | ForEach-Object { $_.id })
Assert 'history contains the sold product' ($histIds -contains $productId) "list=$($histIds -join ',')"
$item = $r.data.list | Where-Object { $_.id -eq $productId } | Select-Object -First 1
Assert 'history item status=OFF_SHELF (3)' ($item.status -eq 3) "status=$($item.status)"

# -----------------------------------------------------
# 12. After off-shelf, non-terminal intent code becomes invalid -> 40905
# -----------------------------------------------------
Write-Host "[12] Code invalid after off-shelf"
$r = Api GET ("/api/buyer/intent?code=" + $codes['buyerC'])
Assert 'code=40905' ($r.code -eq 40905) "code=$($r.code) msg=$($r.message)"

# -----------------------------------------------------
# Summary
# -----------------------------------------------------
Write-Host ""
Write-Host ("==== Summary: PASS={0}  FAIL={1} ====" -f $Global:Pass, $Global:Fail) -ForegroundColor Cyan
if ($Global:Fail -gt 0) { exit 1 } else { exit 0 }
