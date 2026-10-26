param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
function Request([string]$method, [string]$path, [int]$expected, $body = $null, [string]$user = '', [string]$password = '') {
    $headers = @{}
    if ($user) { $headers.Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${user}:${password}")) }
    $args = @{ Method = $method; Uri = "$BaseUrl$path"; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $body) { $args.ContentType = 'application/json'; $args.Body = ($body | ConvertTo-Json -Compress) }
    try { $r = Invoke-WebRequest @args; $status = [int]$r.StatusCode; $content = $r.Content }
    catch {
        if (-not $_.Exception.Response) { throw "Falha de conexão em $method $path" }
        $status = [int]$_.Exception.Response.StatusCode
        $content = ''
    }
    if ($status -ne $expected) { throw "$method ${path}: esperado $expected, recebido $status" }
    Write-Host "$method $path => $status"
    return $content
}
function Login([string]$name, [string]$password, [int]$expected) { Request 'POST' '/login' $expected @{username=$name; senha=$password} | Out-Null }
try {
    Write-Host 'Este roteiro apenas adiciona dados demo; não limpa volumes nem modifica contas existentes.'
    $suffix = [guid]::NewGuid().ToString('N')
    $name = "teste-$suffix"; $blocked = "bloqueado-$suffix"
    $payload = @{username=$name; senha='SenhaInicial!'}
    Request 'GET' '/' 401 | Out-Null
    Request 'GET' '/' 403 $null 'admin' 'Admin123!' | Out-Null
    Request 'POST' '/' 403 $payload 'admin' 'Admin123!' | Out-Null
    $created = Request 'POST' '/' 201 $payload 'manutencao' 'Manut123!' | ConvertFrom-Json
    if (-not $created.id -or $created.id -le 0 -or $created.username -ne $name -or $null -ne $created.senha) { throw 'Resposta de cadastro inválida.' }
    $id = $created.id
    Request 'POST' '/' 409 $payload 'manutencao' 'Manut123!' | Out-Null
    $list = Request 'GET' '/' 200 $null 'manutencao' 'Manut123!'
    $users = @($list | ConvertFrom-Json)
    if (-not ($users | Where-Object { $_.id -eq $id -and $_.username -eq $name })) { throw 'Usuário cadastrado não consta na listagem.' }
    if ($list -match '(?i)senha|\$2[aby]\$|SenhaInicial!') { throw 'Segredo encontrado na listagem.' }
    Request 'PUT' "/unlock/$id" 403 $null 'manutencao' 'Manut123!' | Out-Null
    Request 'PUT' "/trocarsenha/$id" 204 @{atual='SenhaInicial!'; nova='SenhaNova!'} | Out-Null
    Login $name 'SenhaInicial!' 401
    Login $name 'SenhaNova!' 200
    Login $name 'incorreta' 401
    Login $name 'SenhaNova!' 200
    Login $name 'incorreta' 401
    Login $name 'incorreta' 401
    Login $name 'SenhaNova!' 401
    Request 'PUT' "/unlock/$id" 204 $null 'admin' 'Admin123!' | Out-Null
    Login $name 'SenhaNova!' 200
    $other = Request 'POST' '/' 201 @{username=$blocked; senha='OutraSenha!'} 'manutencao' 'Manut123!' | ConvertFrom-Json
    if (-not $other.id -or $other.id -le 0 -or $other.username -ne $blocked) { throw 'Resposta do segundo cadastro inválida.' }
    Login $blocked 'incorreta' 401
    Login $blocked 'incorreta' 401
    Login $blocked 'OutraSenha!' 401
    Write-Host "Roteiro concluído; usuário demo $blocked (ID $($other.id)) permanece bloqueado para o dashboard."
    exit 0
} catch { Write-Error "Roteiro falhou: $($_.Exception.Message)" -ErrorAction Continue; exit 1 }

