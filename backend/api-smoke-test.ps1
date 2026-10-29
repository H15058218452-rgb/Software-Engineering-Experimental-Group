# =====================================================
#  Online Shop Iteration 2 (multi-product) - API smoke test
#
#  Flow: login -> create L1/L2 category -> upload images -> publish product
#        (category+price+stock+gallery) -> buyer views -> 3 buyers submit
#        intents (with productId) -> query by code -> duplicate blocked
#        -> start trade -> trade protections (40906/40907/40908/40909/40910)
#        -> confirm-failed (VOID) promote -> confirm-sold (stock 1 -> auto
#        off-shelf) -> seller lists/history -> code invalid
#
#  Iteration 2 interface changes vs iteration 1:
#    * Multi-product: publish no longer limited to a single active product;
#      SubmitIntentRequest now carries productId (each product has own queue).
#    * Two-level categories: POST /api/seller/category (auth), public
#      GET /api/category/tree; product must be published under a leaf (L2)
#      category (PublishRequest.categoryId is required).
#    * Stock: PublishRequest.stock (>=1); confirm-sold decrements stock and
#      auto off-shelves at 0; new restock / stock-adjust / shelf-on endpoints.
#    * Gallery + rich text: PublishRequest.images (<=9, first is main image),
#      description is sanitized HTML; upload endpoints /api/upload/image,
#      /api/upload/images (batch), /api/upload/media (all require seller auth).
#    * confirm-sold now returns SellerProductView in data (iteration 1
#      returned data=null).
#    * New buyer endpoints: GET /api/buyer/products (paged, category/keyword),
#      GET /api/buyer/products/{id}, PUT /api/buyer/intent,
#      POST /api/buyer/intent/cancel.
#    * New seller endpoints: GET /api/seller/products (paged mgmt list),
#      GET /api/seller/products/{id}, POST /api/product/update etc.
#    * New error codes: 40907 NOT_QUEUE_HEAD (confirm with non-head intent),
#      40910 TRADING_CANNOT_CANCEL, 40911-40917 category/stock/image errors.
#
#  Covered by this script: the full happy path + protection codes listed in
#  the flow above.
#  NOT covered (out of smoke scope, noted here on purpose):
#    * confirm-failed with disposition=REQUEUE (VOID path is tested instead)
#    * /api/upload/media, /api/product/update, /restock, /stock-adjust,
#      /shelf-on, freeze/unfreeze happy path, PUT/DELETE category
#    * Buyer modify/cancel happy path (only the trading-state rejections
#      40906/40910 are asserted)
#
#  Prerequisites:
#    1) MySQL is up; the "shop" schema is initialized.
#    2) Backend (iteration 2) is running at http://localhost:8080
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

# Multipart upload helper (PS 5.1 has no -Form; build the body manually).
# Posts to the batch endpoint POST /api/upload/images (field name "files").
function Upload-Images {
    param(
        [byte[][]]$Files,
        [string[]]$Names,
        [string]$Token
    )
    $boundary = '----SmokeBoundary' + [guid]::NewGuid().ToString('N')
    $enc = [System.Text.Encoding]::ASCII
    $ms = New-Object System.IO.MemoryStream
    try {
        for ($i = 0; $i -lt $Files.Count; $i++) {
            $head = "--$boundary`r`nContent-Disposition: form-data; name=`"files`"; filename=`"$($Names[$i])`"`r`nContent-Type: image/png`r`n`r`n"
            $hb = $enc.GetBytes($head)
            $ms.Write($hb, 0, $hb.Length)
            $ms.Write($Files[$i], 0, $Files[$i].Length)
            $crlf = $enc.GetBytes("`r`n")
            $ms.Write($crlf, 0, $crlf.Length)
        }
        $tail = $enc.GetBytes("--$boundary--`r`n")
        $ms.Write($tail, 0, $tail.Length)
        $body = $ms.ToArray()
    } finally {
        $ms.Dispose()
    }
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        return Invoke-RestMethod -Method POST -Uri "$Base/api/upload/images" -Headers $headers `
            -ContentType "multipart/form-data; boundary=$boundary" -Body $body -UseBasicParsing
    } catch {
        $msg = $_.ErrorDetails.Message
        if ($msg) { return ($msg | ConvertFrom-Json) }
        throw
    }
}

function New-TinyPng {
    # 1x1 transparent PNG (68 bytes), no external file dependency.
    return [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==')
}

Write-Host "==== Online Shop Iteration 2  API smoke test ====" -ForegroundColor Cyan

# -----------------------------------------------------
# 1. Seller login (returns token)
# -----------------------------------------------------
Write-Host "[1] Seller login"
$r = Api POST '/api/seller/login' @{ username = $User; password = $Pwd }
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$token = $r.data.token
Assert 'token not empty' ([bool]$token) "token=$token"

# -----------------------------------------------------
# 2. Create categories (L1 + L2) and read the public tree
# -----------------------------------------------------
Write-Host "[2] Create L1/L2 categories"
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$r = Api POST '/api/seller/category' @{ name = "SmokeCat-$stamp"; parentId = $null; sort = 1; requestId = [guid]::NewGuid().ToString() } $token
Assert 'create L1: code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$l1id = $r.data.id
Assert 'L1 id not empty' ([bool]$l1id) "id=$l1id"
$r = Api POST '/api/seller/category' @{ name = "SmokeSub-$stamp"; parentId = [long]$l1id; sort = 1; requestId = [guid]::NewGuid().ToString() } $token
Assert 'create L2: code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$l2id = $r.data.id
Assert 'L2 parentId = L1 id' ([string]$r.data.parentId -eq [string]$l1id) "parentId=$($r.data.parentId) l1=$l1id"
$r = Api GET '/api/category/tree'
Assert 'public tree contains L2' ($r.code -eq 0 -and @($r.data | Where-Object { [string]$_.id -eq [string]$l1id -and @($_.children | Where-Object { [string]$_.id -eq [string]$l2id }).Count -eq 1 }).Count -eq 1) "code=$($r.code)"

# -----------------------------------------------------
# 3. Upload 2 gallery images (batch endpoint, auth required)
# -----------------------------------------------------
Write-Host "[3] Upload images"
$png1 = New-TinyPng
$png2 = New-TinyPng
$r = Upload-Images -Files @($png1, $png2) -Names @('smoke1.png', 'smoke2.png') -Token $token
Assert 'upload: code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$urls = @($r.data.urls)
Assert 'got 2 urls' ($urls.Count -eq 2) "count=$($urls.Count)"
Assert 'urls start with /uploads/' (($urls | Where-Object { $_ -like '/uploads/*' }).Count -eq 2) "urls=$($urls -join ',')"
$imgOk = $false
try {
    $resp = Invoke-WebRequest -Uri "$Base$($urls[0])" -UseBasicParsing -TimeoutSec 15
    $imgOk = ($resp.StatusCode -eq 200)
} catch { $imgOk = $false }
Assert 'GET uploaded image returns HTTP 200' $imgOk "url=$($urls[0])"

# -----------------------------------------------------
# 4. Publish product (L2 category, price, stock=1, 2-image gallery)
# -----------------------------------------------------
Write-Host "[4] Publish product"
$pname = 'SmokeTestProduct-' + $stamp
$r = Api POST '/api/product/publish' @{
    name        = $pname
    categoryId  = [long]$l2id
    description = '<p>smoke test <b>rich</b> text</p>'
    price       = '99.90'
    stock       = 1
    images      = $urls
    requestId   = [guid]::NewGuid().ToString()
} $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
$productId = $r.data.id
Assert 'product is ON_SALE (status 0)' ($r.data.status -eq 0) "status=$($r.data.status)"
Assert 'stock=1' ($r.data.stock -eq 1) "stock=$($r.data.stock)"
Assert 'categoryId matches L2' ([string]$r.data.categoryId -eq [string]$l2id) "categoryId=$($r.data.categoryId) l2=$l2id"
Assert 'gallery has 2 images' (@($r.data.images).Count -eq 2) "images=$($r.data.images)"
Assert 'main image is first uploaded url' ($r.data.imageUrl -eq $urls[0]) "imageUrl=$($r.data.imageUrl)"

# -----------------------------------------------------
# 5. Buyer views: product detail + paged list filtered by L1 category
# -----------------------------------------------------
Write-Host "[5] Buyer views product"
$r = Api GET "/api/buyer/products/$productId"
Assert 'detail: code=0' ($r.code -eq 0) "code=$($r.code)"
Assert 'canSubmitIntent=true' ($r.data.canSubmitIntent -eq $true) "canSubmitIntent=$($r.data.canSubmitIntent)"
Assert 'detail gallery has 2 images' (@($r.data.images).Count -eq 2) "images=$($r.data.images)"
$r = Api GET "/api/buyer/products?categoryId=$l1id&page=1&pageSize=10"
Assert 'list by L1 category contains product' ($r.code -eq 0 -and (@($r.data.list) | Where-Object { [string]$_.id -eq [string]$productId }).Count -eq 1) "code=$($r.code) total=$($r.data.total)"

# -----------------------------------------------------
# 6. Three buyers submit intents (with productId, FIFO positions 1/2/3)
# -----------------------------------------------------
Write-Host "[6] Three buyers submit intents"
$codes    = @{}
$ids      = @{}
$expected = @{ 'Alice' = 1; 'Bob' = 2; 'Carol' = 3 }
$buyers   = @(
    @{ label = 'Alice'; p = '13800000001' },
    @{ label = 'Bob';   p = '13800000002' },
    @{ label = 'Carol'; p = '13800000003' }
)
foreach ($b in $buyers) {
    $r = Api POST '/api/buyer/intent' @{ name = $b.label; phone = $b.p; productId = [long]$productId; requestId = [guid]::NewGuid().ToString() }
    Assert "submit $($b.label): code=0" ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
    $codes[$b.label] = $r.data.code
    $ids[$b.label]   = $r.data.intent.id
    Assert "$($b.label) position=$($expected[$b.label])" ($r.data.intent.queuePosition -eq $expected[$b.label]) "queuePosition=$($r.data.intent.queuePosition)"
    Assert "$($b.label) got one-time code" ([bool]$r.data.code)
}

# -----------------------------------------------------
# 7. Query intent by code (position + product fields)
# -----------------------------------------------------
Write-Host "[7] Query intent by code"
$r = Api GET ("/api/buyer/intent?code=" + $codes['Alice'])
Assert 'code=0' ($r.code -eq 0) "code=$($r.code)"
Assert 'position=1' ($r.data.queuePosition -eq 1) "queuePosition=$($r.data.queuePosition)"
Assert 'canModify=true and canCancel=true' ($r.data.canModify -eq $true -and $r.data.canCancel -eq $true) "canModify=$($r.data.canModify) canCancel=$($r.data.canCancel)"
Assert 'product fields present (name/price/createdAt)' ([bool]$r.data.productName -and [bool]$r.data.productPrice -and [bool]$r.data.createdAt) "productName=$($r.data.productName) productPrice=$($r.data.productPrice) createdAt=$($r.data.createdAt)"

# -----------------------------------------------------
# 8. Duplicate submit (same phone + same product) -> 40904
# -----------------------------------------------------
Write-Host "[8] Duplicate submit blocked (same phone + product)"
$r = Api POST '/api/buyer/intent' @{ name = 'Alice'; phone = '13800000001'; productId = [long]$productId }
Assert 'code=40904' ($r.code -eq 40904) "code=$($r.code) msg=$($r.message)"

# -----------------------------------------------------
# 9. Start trade (product TRADING status 2, head enters trade)
# -----------------------------------------------------
Write-Host "[9] Start trade"
$r = Api POST '/api/product/start-trade' @{ productId = [long]$productId; requestId = [guid]::NewGuid().ToString() } $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
Assert 'product TRADING (status 2)' ($r.data.status -eq 2) "status=$($r.data.status)"
Assert 'head is Alice' ([string]$r.data.currentIntent.id -eq [string]$ids['Alice']) "currentIntent=$($r.data.currentIntent.id) Alice=$($ids['Alice'])"

# -----------------------------------------------------
# 10. Protections during trade:
#     unfreeze -> 40908, off-shelf -> 40909,
#     head buyer cancel -> 40910, head buyer modify -> 40906,
#     confirm-sold with non-head intent -> 40907
# -----------------------------------------------------
Write-Host "[10] Actions blocked during trade"
$r = Api POST '/api/product/unfreeze' @{ productId = [long]$productId } $token
Assert 'unfreeze during trade -> 40908' ($r.code -eq 40908) "code=$($r.code)"
$r = Api POST '/api/product/off-shelf' @{ productId = [long]$productId } $token
Assert 'off-shelf during trade -> 40909' ($r.code -eq 40909) "code=$($r.code)"
$r = Api POST '/api/buyer/intent/cancel' @{ code = $codes['Alice'] }
Assert 'trading buyer cancel -> 40910' ($r.code -eq 40910) "code=$($r.code) msg=$($r.message)"
$r = Api PUT '/api/buyer/intent' @{ code = $codes['Alice']; name = 'Alice2' }
Assert 'trading buyer modify -> 40906' ($r.code -eq 40906) "code=$($r.code) msg=$($r.message)"
$r = Api POST '/api/product/confirm-sold' @{ productId = [long]$productId; intentId = [long]$ids['Bob']; requestId = [guid]::NewGuid().ToString() } $token
Assert 'confirm-sold with non-head intent -> 40907' ($r.code -eq 40907) "code=$($r.code) msg=$($r.message)"

# -----------------------------------------------------
# 11. Confirm failed (VOID) -> Alice voided, Bob auto-promoted (stays TRADING)
# -----------------------------------------------------
Write-Host "[11] Confirm failed (VOID) and promote"
$r = Api POST '/api/product/confirm-failed' @{ productId = [long]$productId; intentId = [long]$ids['Alice']; disposition = 'VOID'; requestId = [guid]::NewGuid().ToString() } $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
Assert 'product still TRADING (status 2)' ($r.data.status -eq 2) "status=$($r.data.status)"
Assert 'Bob promoted to head' ([string]$r.data.currentIntent.id -eq [string]$ids['Bob']) "currentIntent=$($r.data.currentIntent.id) Bob=$($ids['Bob'])"

# -----------------------------------------------------
# 12. Confirm sold -> stock 1 -> 0 -> auto off-shelf (status 3)
#     (iteration 2: returns SellerProductView, not null data)
# -----------------------------------------------------
Write-Host "[12] Confirm sold (stock 1 -> auto off-shelf)"
$r = Api POST '/api/product/confirm-sold' @{ productId = [long]$productId; intentId = [long]$ids['Bob']; requestId = [guid]::NewGuid().ToString() } $token
Assert 'code=0' ($r.code -eq 0) "code=$($r.code) msg=$($r.message)"
Assert 'stock decremented to 0' ($r.data.stock -eq 0) "stock=$($r.data.stock)"
Assert 'product OFF_SHELF (status 3)' ($r.data.status -eq 3) "status=$($r.data.status)"

# -----------------------------------------------------
# 13. Seller lists/history: mgmt list (status=3), history products,
#     history intents (VOIDED / SUCCESS / FINAL_FAILED)
# -----------------------------------------------------
Write-Host "[13] Seller lists and history"
$r = Api GET '/api/seller/products?status=3&page=1&pageSize=10' $null $token
Assert 'mgmt list (status=3) contains product' ($r.code -eq 0 -and (@($r.data.list) | Where-Object { [string]$_.id -eq [string]$productId }).Count -eq 1) "code=$($r.code)"
$r = Api GET '/api/seller/history/products?page=1&pageSize=10' $null $token
Assert 'history: code=0' ($r.code -eq 0) "code=$($r.code)"
$histIds = @($r.data.list | Where-Object { $_ } | ForEach-Object { [string]$_.id })
Assert 'history contains the sold product' ($histIds -contains [string]$productId) "list=$($histIds -join ',')"
$item = $r.data.list | Where-Object { [string]$_.id -eq [string]$productId } | Select-Object -First 1
Assert 'history item status=OFF_SHELF (3)' ($item.status -eq 3) "status=$($item.status)"
$r = Api GET "/api/seller/history/products/$productId/intents" $null $token
$intents = @($r.data)
Assert 'history intents: code=0 and 3 records' ($r.code -eq 0 -and $intents.Count -eq 3) "code=$($r.code) count=$($intents.Count)"
Assert 'Alice VOIDED (3), Bob SUCCESS (2), Carol FINAL_FAILED (6)' (
    (@($intents | Where-Object { [string]$_.id -eq [string]$ids['Alice'] -and $_.status -eq 3 }).Count -eq 1) -and
    (@($intents | Where-Object { [string]$_.id -eq [string]$ids['Bob']   -and $_.status -eq 2 }).Count -eq 1) -and
    (@($intents | Where-Object { [string]$_.id -eq [string]$ids['Carol'] -and $_.status -eq 6 }).Count -eq 1)
) "intents=$($intents | ForEach-Object { $_.id + ':' + $_.status } | Out-String)"

# -----------------------------------------------------
# 14. After off-shelf, Carol's terminal intent code -> 40905
# -----------------------------------------------------
Write-Host "[14] Code invalid after off-shelf"
$r = Api GET ("/api/buyer/intent?code=" + $codes['Carol'])
Assert 'code=40905' ($r.code -eq 40905) "code=$($r.code) msg=$($r.message)"

# -----------------------------------------------------
# Summary
# -----------------------------------------------------
Write-Host ""
Write-Host ("==== Summary: PASS={0}  FAIL={1} ====" -f $Global:Pass, $Global:Fail) -ForegroundColor Cyan
if ($Global:Fail -gt 0) { exit 1 } else { exit 0 }
